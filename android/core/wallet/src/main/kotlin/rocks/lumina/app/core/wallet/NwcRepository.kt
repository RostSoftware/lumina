package rocks.lumina.app.core.wallet

import com.vitorpamplona.quartz.nip47WalletConnect.Nip47Client
import com.vitorpamplona.quartz.nip47WalletConnect.Nip47WalletConnect
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcRequestEvent
import com.vitorpamplona.quartz.nip47WalletConnect.events.NwcResponseEvent
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.GetBalanceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.IErrorResponseLike
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.PayInvoiceSuccessResponse
import com.vitorpamplona.quartz.nip47WalletConnect.rpc.Response
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.signer.SecureStore

/** Non-secret description of the connected wallet. */
public data class WalletInfo(val walletPubkey: String, val relay: String, val lud16: String?)

public class WalletException(message: String) : Exception(message)

/**
 * Nostr Wallet Connect (NIP-47). Quartz builds/decrypts requests (`Nip47Client`); this class is the
 * transport (gap #4): subscribe to the response on the wallet relay, publish the request, await with a
 * timeout. The connection URI is a secret and is stored encrypted per account.
 */
@Singleton
public class NwcRepository @Inject constructor(
    private val network: NostrNetwork,
    private val secureStore: SecureStore,
    private val accountManager: AccountManager,
    @ApplicationScope scope: CoroutineScope,
) {
    private val _wallet = MutableStateFlow<WalletInfo?>(null)
    public val wallet: StateFlow<WalletInfo?> = _wallet.asStateFlow()

    init {
        scope.launch {
            accountManager.active.map { it?.pubkey }.distinctUntilChanged().collect { pk ->
                _wallet.value = pk?.let { secureStore.get(key(it)) }?.let(::infoOf)
            }
        }
    }

    private fun key(pubkey: String) = "nwc:$pubkey"

    private fun infoOf(uri: String): WalletInfo? = runCatching {
        val parsed = Nip47WalletConnect.parse(uri)
        WalletInfo(parsed.pubKeyHex, parsed.relayUri.url, parsed.lud16)
    }.getOrNull()

    private fun client(): Nip47Client {
        val pk = accountManager.activePubkey ?: throw WalletException("Not logged in")
        val uri = secureStore.get(key(pk)) ?: throw WalletException("No wallet connected")
        return Nip47Client.fromUri(uri)
    }

    /** Validates `nostr+walletconnect://…`, checks the wallet answers, and stores it encrypted. */
    public suspend fun connect(uri: String): WalletInfo {
        val pk = accountManager.activePubkey ?: throw WalletException("Not logged in")
        val trimmed = uri.trim()
        val info = infoOf(trimmed) ?: throw WalletException("Invalid wallet connection string")
        val client = runCatching { Nip47Client.fromUri(trimmed) }.getOrElse { throw WalletException("Connection string has no secret") }
        val response = request(client, client.getBalance(), timeoutMs = 20_000)
        if (response is IErrorResponseLike) {
            val message = response.errorMessage().orEmpty()
            // Wallets that restrict get_balance still work for payments.
            if (!message.contains("NOT_IMPLEMENTED", true) && !message.contains("RESTRICTED", true) && !message.contains("UNAUTHORIZED", true)) {
                throw WalletException(message.ifBlank { "Wallet error" })
            }
        }
        secureStore.put(key(pk), trimmed)
        _wallet.value = info
        return info
    }

    public fun disconnect() {
        val pk = accountManager.activePubkey ?: return
        secureStore.remove(key(pk))
        _wallet.value = null
    }

    /** Balance in sats, or null when the wallet doesn't allow it. */
    public suspend fun balanceSats(): Long? {
        val client = client()
        val response = request(client, client.getBalance())
        return (response as? GetBalanceSuccessResponse)?.result?.balance?.div(1000)
    }

    /** Pays [bolt11]; returns the preimage (may be null for some wallets). Throws [WalletException]. */
    public suspend fun payInvoice(bolt11: String): String? {
        val client = client()
        val response = request(client, client.payInvoice(bolt11), timeoutMs = 60_000)
        if (response is IErrorResponseLike) throw WalletException(response.errorMessage() ?: "Payment failed")
        return (response as? PayInvoiceSuccessResponse)?.result?.preimage
    }

    private suspend fun request(client: Nip47Client, request: NwcRequestEvent, timeoutMs: Long = 30_000): Response = withContext(Dispatchers.IO) {
        val relays = listOf(client.relayUrl.url)
        val response = withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val awaiting = async {
                    network.stream(relays, listOf(client.responseFilter(request.id))).filterIsInstance<NwcResponseEvent>().first()
                }
                delay(300) // let the REQ reach the relay before the request is published
                val accepted = network.publish(request, relays)
                if (accepted.isEmpty()) {
                    awaiting.cancel()
                    throw WalletException("Wallet relay unreachable")
                }
                awaiting.await()
            }
        } ?: throw WalletException("The wallet did not respond")
        client.parseResponse(response)
    }
}
