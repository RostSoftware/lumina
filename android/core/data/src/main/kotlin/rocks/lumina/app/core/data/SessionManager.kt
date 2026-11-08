package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.signer.SignerType
import rocks.lumina.app.core.signer.TrackedSigner

/**
 * Reacts to account switches: resets per-account state and (re)loads the user's relay lists, follows,
 * mute list, bookmarks and profile; restarts the notification and DM watchers. Also answers NIP-42 AUTH.
 */
@Singleton
public class SessionManager @Inject constructor(
    private val accountManager: AccountManager,
    private val relays: RelayRepository,
    private val contacts: ContactRepository,
    private val lists: ListsRepository,
    private val profiles: ProfileRepository,
    private val notifications: NotificationRepository,
    private val dms: DmRepository,
    private val client: NostrClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private var started = false
    private var authenticator: RelayAuthenticator? = null

    public fun start() {
        if (started) return
        started = true
        authenticator = RelayAuthenticator(client, scope) { _, template, interactive ->
            val active = accountManager.active.value ?: return@RelayAuthenticator emptyList()
            // Never prompt a remote signer for silent re-auth.
            if (!interactive && active.account.type != SignerType.INTERNAL) return@RelayAuthenticator emptyList()
            val signer = (active.signer as? TrackedSigner)?.inner ?: active.signer
            listOfNotNull(runCatching { signer.sign(template) }.getOrNull())
        }
        scope.launch {
            accountManager.active.map { it?.pubkey }.distinctUntilChanged().collectLatest { pubkey ->
                relays.reset()
                contacts.reset()
                lists.reset()
                dms.reset()
                notifications.stopUnreadWatch()
                if (pubkey == null) return@collectLatest
                coroutineScope {
                    relays.loadOwn(pubkey)
                    launch { runCatching { contacts.loadOwn(pubkey) } }
                    launch { runCatching { lists.loadOwn(pubkey) } }
                    launch { runCatching { profiles.refresh(pubkey) } }
                    notifications.startUnreadWatch(pubkey)
                    dms.startUnreadWatch(pubkey)
                }
            }
        }
    }

    /** Reloads the user's lists (pull-to-refresh on settings screens). */
    public suspend fun reload() {
        val pubkey = accountManager.activePubkey ?: return
        runCatching { relays.loadOwn(pubkey) }
        runCatching { contacts.loadOwn(pubkey) }
        runCatching { lists.loadOwn(pubkey) }
    }
}
