package rocks.lumina.app.core.signer

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.decodePrivateKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.decodePublicKeyAsHexOrNull
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip46RemoteSigner.BunkerClientMetadata
import com.vitorpamplona.quartz.nip46RemoteSigner.NostrConnectURI
import com.vitorpamplona.quartz.nip46RemoteSigner.signer.NostrSignerRemote
import com.vitorpamplona.quartz.nip55AndroidSigner.client.NostrSignerExternal
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.di.ApplicationScope

/** The logged-in account together with its (tracked) signer. */
public data class ActiveAccount(val account: Account, val signer: NostrSigner) {
    val pubkey: String get() = account.pubkey
}

/**
 * Multi-account store and signer factory. Secrets go to [SecureStore]; metadata to DataStore.
 * Switching accounts swaps the signer; every repository observes [active] and restarts.
 */
@Singleton
public class AccountManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val secureStore: SecureStore,
    private val monitor: SignerMonitor,
    private val network: NostrNetwork,
    private val client: NostrClient,
    private val bridge: ExternalSignerBridge,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("lumina_accounts") }
    private val accountsKey = stringPreferencesKey("accounts")
    private val activeKey = stringPreferencesKey("active")
    private val mutex = Mutex()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    public val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _active = MutableStateFlow<ActiveAccount?>(null)
    public val active: StateFlow<ActiveAccount?> = _active.asStateFlow()

    private val _ready = MutableStateFlow(false)
    public val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _loginRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when a logged-out user tries an action that needs a signer; the app opens the login flow. */
    public val loginRequests: SharedFlow<Unit> = _loginRequests.asSharedFlow()

    private val _loggedOut = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Pubkeys whose account was removed; data layers wipe that account's caches. */
    public val loggedOut: SharedFlow<String> = _loggedOut.asSharedFlow()

    init {
        scope.launch(Dispatchers.IO) { load() }
    }

    public val activePubkey: String? get() = _active.value?.pubkey

    public suspend fun awaitReady() {
        ready.first { it }
    }

    public fun signerOrNull(): NostrSigner? = _active.value?.signer

    /** Returns the active signer or emits a login request and throws [SignerFailureException]. */
    public fun requireSigner(): NostrSigner {
        val signer = _active.value?.signer
        if (signer == null) {
            _loginRequests.tryEmit(Unit)
            throw SignerFailureException(SignerFailure.NO_SIGNER)
        }
        return signer
    }

    public fun requestLogin() {
        _loginRequests.tryEmit(Unit)
    }

    // ---- login methods -------------------------------------------------------------------

    /** nsec1… or 64-hex private key. The key is stored encrypted with the Android Keystore. */
    public suspend fun loginWithNsec(input: String): Account = withContext(Dispatchers.Default) {
        val privHex = decodePrivateKeyAsHexOrNull(input.trim()) ?: throw IllegalArgumentException("invalid nsec")
        val keyPair = KeyPair(privHex.hexToByteArray())
        val pubkey = keyPair.pubKey.toHexKey()
        secureStore.put(nsecKey(pubkey), privHex)
        addAndActivate(Account(pubkey, SignerType.INTERNAL, addedAt = now()))
    }

    /** Generates a new keypair; the caller then publishes a profile. */
    public suspend fun createAccount(): Account = withContext(Dispatchers.Default) {
        val keyPair = KeyPair()
        val privKey = keyPair.privKey ?: throw IllegalStateException("key generation failed")
        val pubkey = keyPair.pubKey.toHexKey()
        secureStore.put(nsecKey(pubkey), privKey.toHexKey())
        addAndActivate(Account(pubkey, SignerType.INTERNAL, addedAt = now()))
    }

    /** NIP-55: [pubkeyOrNpub] and [packageName] come from the signer's `get_public_key` result. */
    public suspend fun loginWithAmber(pubkeyOrNpub: String, packageName: String): Account {
        val pubkey = decodePublicKeyAsHexOrNull(pubkeyOrNpub.trim()) ?: throw IllegalArgumentException("invalid pubkey")
        return addAndActivate(Account(pubkey, SignerType.AMBER, packageName = packageName, addedAt = now()))
    }

    /** NIP-46 `bunker://`: connect, then `get_public_key`. Throws on rejection/timeouts. */
    public suspend fun loginWithBunker(uri: String): Account = withContext(Dispatchers.IO) {
        val parsed = NostrConnectURI.parseBunker(uri.trim()) ?: throw IllegalArgumentException("invalid bunker uri")
        if (parsed.relays.isEmpty()) throw IllegalArgumentException("bunker uri has no relays")
        val clientKeys = KeyPair()
        val remote = NostrSignerRemote(
            signer = NostrSignerInternal(clientKeys),
            remotePubkey = parsed.remoteSignerPubKey.lowercase(),
            relays = parsed.relays,
            client = client,
            permissions = Nip46Permissions.PERMS,
            secret = parsed.secret,
            clientMetadata = CLIENT_METADATA,
        )
        try {
            remote.openSubscription()
            val pubkey = withTimeout(CONNECT_TIMEOUT_MS) {
                remote.connect()
                remote.getPublicKey()
            }
            storeNip46(pubkey, clientKeys, parsed.remoteSignerPubKey.lowercase(), parsed.relays.map { it.url })
        } finally {
            remote.closeSubscription()
        }
    }

    /** Starts a client-initiated nostrconnect handshake using the app's write relays (same as web). */
    public fun newNostrConnectSession(): NostrConnectSession =
        NostrConnectSession(RelayDefaults.appRelays.filter { it.write }.map { it.url }, KeyPair())

    /** Waits for the signer to scan/open the nostrconnect URI, then finishes login. */
    public suspend fun completeNostrConnect(session: NostrConnectSession): Account = withContext(Dispatchers.IO) {
        val remotePubkey = session.awaitRemoteSigner(network)
        val relays = session.relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()
        val remote = NostrSignerRemote(
            signer = NostrSignerInternal(session.clientKeys),
            remotePubkey = remotePubkey,
            relays = relays,
            client = client,
            permissions = Nip46Permissions.PERMS,
            clientMetadata = CLIENT_METADATA,
        )
        try {
            remote.openSubscription()
            val pubkey = withTimeout(CONNECT_TIMEOUT_MS) { remote.getPublicKey() }
            storeNip46(pubkey, session.clientKeys, remotePubkey, session.relays)
        } finally {
            remote.closeSubscription()
        }
    }

    private suspend fun storeNip46(pubkey: String, clientKeys: KeyPair, remotePubkey: String, relays: List<String>): Account {
        val priv = clientKeys.privKey ?: throw IllegalStateException("missing client key")
        secureStore.put(nip46Key(pubkey), priv.toHexKey())
        return addAndActivate(Account(pubkey, SignerType.NIP46, remotePubkey = remotePubkey, bunkerRelays = relays, addedAt = now()))
    }

    // ---- account switching --------------------------------------------------------------

    public suspend fun switchTo(pubkey: String) {
        mutex.withLock {
            val account = _accounts.value.firstOrNull { it.pubkey == pubkey } ?: return
            activate(account)
            persist()
        }
    }

    /** Logs out [pubkey]: wipes its keys/tokens, removes it, and activates the next account (if any). */
    public suspend fun logout(pubkey: String) {
        mutex.withLock {
            val wasActive = _active.value?.pubkey == pubkey
            if (wasActive) closeActive()
            secureStore.removeAllFor(pubkey)
            _accounts.value = _accounts.value.filterNot { it.pubkey == pubkey }
            if (wasActive) {
                val next = _accounts.value.firstOrNull()
                if (next != null) activate(next) else _active.value = null
            }
            persist()
        }
        _loggedOut.emit(pubkey)
    }

    /** nsec for the explicit "Back up key" flow (internal accounts only). */
    public fun exportNsec(pubkey: String): String? =
        secureStore.get(nsecKey(pubkey))?.let { runCatching { it.hexToByteArray().toNsec() }.getOrNull() }

    public fun secureStore(): SecureStore = secureStore

    // ---- internals ---------------------------------------------------------------------

    private suspend fun addAndActivate(account: Account): Account = mutex.withLock {
        _accounts.value = _accounts.value.filterNot { it.pubkey == account.pubkey } + account
        activate(account)
        persist()
        account
    }

    private fun activate(account: Account) {
        closeActive()
        val signer = buildSigner(account)
        _active.value = signer?.let { ActiveAccount(account, it) }
    }

    private fun closeActive() {
        val current = _active.value?.signer as? TrackedSigner ?: return
        (current.inner as? NostrSignerRemote)?.closeSubscription()
        if (current.inner is NostrSignerExternal) bridge.setResultHandler(null)
    }

    private fun buildSigner(account: Account): TrackedSigner? = runCatching {
        when (account.type) {
            SignerType.INTERNAL -> {
                val priv = secureStore.get(nsecKey(account.pubkey)) ?: return null
                TrackedSigner(NostrSignerInternal(KeyPair(priv.hexToByteArray())), monitor, remote = false)
            }
            SignerType.AMBER -> {
                val external = NostrSignerExternal(account.pubkey, account.packageName.orEmpty(), context.contentResolver)
                val launcher: (android.content.Intent) -> Unit = { intent -> bridge.launch(intent) }
                external.registerForegroundLauncher(launcher)
                bridge.setResultHandler { data -> external.newResponse(data) }
                TrackedSigner(external, monitor, remote = true)
            }
            SignerType.NIP46 -> {
                val priv = secureStore.get(nip46Key(account.pubkey)) ?: return null
                val remotePubkey = account.remotePubkey ?: return null
                val remote = NostrSignerRemote(
                    signer = NostrSignerInternal(KeyPair(priv.hexToByteArray())),
                    remotePubkey = remotePubkey,
                    relays = account.bunkerRelays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet(),
                    client = client,
                    permissions = Nip46Permissions.PERMS,
                    clientMetadata = CLIENT_METADATA,
                )
                remote.bindUserPubkey(account.pubkey)
                remote.openSubscription()
                TrackedSigner(remote, monitor, remote = true)
            }
        }
    }.getOrNull()

    private suspend fun load() {
        val prefs = dataStore.data.first()
        val list = prefs[accountsKey]?.let {
            runCatching { json.decodeFromString(ListSerializer(Account.serializer()), it) }.getOrNull()
        } ?: emptyList()
        val activePubkey = prefs[activeKey]
        mutex.withLock {
            _accounts.value = list
            val account = list.firstOrNull { it.pubkey == activePubkey } ?: list.firstOrNull()
            if (account != null) activate(account)
        }
        _ready.value = true
    }

    private suspend fun persist() {
        val list = _accounts.value
        val active = _active.value?.pubkey
        dataStore.edit { prefs ->
            prefs[accountsKey] = json.encodeToString(ListSerializer(Account.serializer()), list)
            if (active != null) prefs[activeKey] = active else prefs.remove(activeKey)
        }
    }

    private fun now(): Long = System.currentTimeMillis() / 1000

    private companion object {
        const val CONNECT_TIMEOUT_MS = 120_000L
        val CLIENT_METADATA = BunkerClientMetadata(name = "LUMINA", url = "https://lumina.rocks")

        fun nsecKey(pubkey: String) = "nsec:$pubkey"
        fun nip46Key(pubkey: String) = "nip46:$pubkey"
    }
}
