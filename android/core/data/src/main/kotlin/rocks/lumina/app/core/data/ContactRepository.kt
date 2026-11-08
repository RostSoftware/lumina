package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.signer.AccountManager

/**
 * Kind 3 follow list. **Follow/unfollow always fetches the newest kind 3 from relays first**, so a stale
 * local copy never wipes the user's follows. Updates are optimistic and roll back on failure.
 */
@Singleton
public class ContactRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val publisher: Publisher,
    private val accountManager: AccountManager,
) {
    private val mutex = Mutex()
    private val _contactList = MutableStateFlow<Event?>(null)
    private val _following = MutableStateFlow<Set<String>>(emptySet())
    public val following: StateFlow<Set<String>> = _following.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    /** True once the follow list was loaded (from cache or relays) for the active account. */
    public val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    public fun reset() {
        _contactList.value = null
        _following.value = emptySet()
        _loaded.value = false
    }

    private fun apply(event: Event?) {
        if (event == null) return
        val current = _contactList.value
        if (current != null && current.createdAt > event.createdAt) return
        _contactList.value = event
        _following.value = pTags(event)
    }

    private fun pTags(event: Event): Set<String> = event.tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }.filter(UrlSafety::isHex64).toSet()

    public suspend fun loadOwn(pubkey: String) {
        val filter = Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey))
        apply(cache.query(filter).maxByOrNull { it.createdAt })
        if (_contactList.value != null) _loaded.value = true
        val fresh = fetchNewestOwn(pubkey)
        apply(fresh)
        _loaded.value = true
    }

    private suspend fun fetchNewestOwn(pubkey: String): Event? {
        val filter = Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey))
        val events = network.query((relays.writeRelays() + relays.discoveryRelays()).distinct(), listOf(filter))
        cache.insert(events)
        return events.maxByOrNull { it.createdAt }
    }

    public suspend fun follow(target: String): Unit = mutate(target, add = true)

    public suspend fun unfollow(target: String): Unit = mutate(target, add = false)

    private suspend fun mutate(target: String, add: Boolean) = mutex.withLock {
        val me = accountManager.requireSigner().pubKey
        val previous = _following.value
        _following.value = if (add) previous + target else previous - target
        try {
            val newest = listOfNotNull(fetchNewestOwn(me), _contactList.value).maxByOrNull { it.createdAt }
            val tags = newest?.tags?.map { it } ?: emptyList()
            val withoutTarget = tags.filterNot { it.size > 1 && it[0] == "p" && it[1] == target }
            val newTags = if (add) withoutTarget + listOf(arrayOf("p", target)) else withoutTarget
            val event = publisher.signAndPublish(
                Kinds.CONTACTS,
                newest?.content.orEmpty(),
                newTags,
                extraRelays = RelayDefaults.indexerRelays,
                notifyTagged = false,
            )
            apply(event)
        } catch (e: Exception) {
            _following.value = previous
            throw e
        }
    }

    /** Pubkeys followed by [pubkey]. */
    public suspend fun followingOf(pubkey: String): List<String> {
        if (pubkey == accountManager.activePubkey && _contactList.value != null) return _following.value.toList()
        val filter = Filter(kinds = listOf(Kinds.CONTACTS), authors = listOf(pubkey))
        val cached = cache.query(filter).maxByOrNull { it.createdAt }
        val fresh = network.query((relays.discoveryRelays() + relays.relaysForAuthor(pubkey)).distinct(), listOf(filter))
        cache.insert(fresh)
        val newest = (listOfNotNull(cached) + fresh).maxByOrNull { it.createdAt } ?: return emptyList()
        return pTags(newest).toList()
    }

    /** Pubkeys whose kind 3 lists [pubkey] (approximate: limited to what read relays return). */
    public suspend fun followersOf(pubkey: String, limit: Int = 500): List<String> {
        val filter = Filter(kinds = listOf(Kinds.CONTACTS), tags = mapOf("p" to listOf(pubkey)), limit = limit)
        val events = network.query(relays.discoveryRelays(), listOf(filter), idleTimeoutMs = 3_000, maxWaitMs = 9_000)
        return events.map { it.pubKey }.distinct()
    }
}
