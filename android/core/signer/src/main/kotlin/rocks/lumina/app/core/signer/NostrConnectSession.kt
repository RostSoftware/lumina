package rocks.lumina.app.core.signer

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerResponse
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectEvent
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import java.security.SecureRandom
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import rocks.lumina.app.core.nostr.NostrNetwork

/**
 * Client-initiated NIP-46 handshake (Quartz gap #5). LUMINA generates an ephemeral client keypair and a
 * secret, shows `nostrconnect://…` (QR + "Open in signer"), then waits for the signer's `connect`
 * response on the URI relays. The response must echo [secret]; its author is the remote signer.
 */
public class NostrConnectSession internal constructor(
    public val relays: List<String>,
    internal val clientKeys: KeyPair,
) {
    public val secret: String = randomSecret()
    public val clientPubkey: String = clientKeys.pubKey.toHexKey()

    public val uri: String = NostrConnectURI.buildNostrConnect(
        clientPubKey = clientPubkey,
        relays = relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) },
        secret = secret,
        perms = Nip46Permissions.PERMS,
        name = "LUMINA",
        url = "https://lumina.rocks",
    )

    /** Suspends until a signer accepts; returns the remote signer pubkey. Cancel to abort. */
    internal suspend fun awaitRemoteSigner(network: NostrNetwork): String {
        val transport = NostrSignerInternal(clientKeys)
        return network.stream(
            relays,
            listOf(Filter(kinds = listOf(NostrConnectEvent.KIND), tags = mapOf("p" to listOf(clientPubkey)))),
        ).filterIsInstance<NostrConnectEvent>()
            .mapNotNull { event ->
                val message = runCatching { event.decryptMessage(transport) }.getOrNull() as? BunkerResponse
                    ?: return@mapNotNull null
                // NIP-46: the signer answers `connect` with the secret. Some signers answer "ack".
                if (message.error == null && (message.result == secret || message.result == "ack")) event.pubKey else null
            }
            .first()
    }

    private companion object {
        fun randomSecret(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.toHexKey()
        }
    }
}

/** Permissions requested from remote signers (NIP-46 `perms`, NIP-55 permission list). */
public object Nip46Permissions {
    public val SIGN_KINDS: List<Int> = listOf(0, 3, 5, 7, 13, 14, 16, 20, 1111, 1984, 9734, 10000, 10002, 10003, 10050, 10063, 22242, 24242)

    public val PERMS: String = (SIGN_KINDS.map { "sign_event:$it" } + listOf("nip44_encrypt", "nip44_decrypt", "nip04_encrypt", "nip04_decrypt")).joinToString(",")
}
