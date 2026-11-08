package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.utils.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton
import rocks.lumina.app.core.signer.AccountManager

/** No relay accepted the event. */
public class PublishFailedException(message: String = "No relay accepted the event") : Exception(message)

/**
 * Signs with the active signer (pending/rejected/timeout surface through `SignerMonitor`) and publishes
 * using the outbox model: the user's write relays plus the read relays of every tagged user.
 */
@Singleton
public class Publisher @Inject constructor(
    private val accountManager: AccountManager,
    private val network: rocks.lumina.app.core.nostr.NostrNetwork,
    private val relays: RelayRepository,
    private val cache: EventCache,
) {
    public suspend fun sign(kind: Int, content: String, tags: List<Array<String>>, createdAt: Long = TimeUtils.now()): Event {
        val signer = accountManager.requireSigner()
        return signer.sign(createdAt, kind, tags.toTypedArray(), content)
    }

    /** Publishes an already-signed event. Throws [PublishFailedException] when no relay accepts it. */
    public suspend fun publish(event: Event, extraRelays: Collection<String> = emptyList(), notifyTagged: Boolean = true): Set<String> {
        val tagged = if (notifyTagged) event.tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] } else emptyList()
        val inboxes = if (tagged.isNotEmpty()) relays.inboxRelaysOf(tagged.filter { it != event.pubKey }) else emptyList()
        val targets = (relays.writeRelays() + extraRelays + inboxes).distinct()
        val accepted = network.publish(event, targets)
        if (accepted.isEmpty()) throw PublishFailedException()
        cache.insert(event)
        return accepted
    }

    public suspend fun signAndPublish(
        kind: Int,
        content: String,
        tags: List<Array<String>>,
        extraRelays: Collection<String> = emptyList(),
        notifyTagged: Boolean = true,
    ): Event {
        val event = sign(kind, content, tags)
        publish(event, extraRelays, notifyTagged)
        return event
    }
}
