package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.model.RelayEntry
import rocks.lumina.app.core.nostr.model.RelayListParser

/** Relay lists announced by some author. */
public data class AuthorRelays(
    val pubkey: String,
    val read: List<String>,
    val write: List<String>,
    val dm: List<String>,
)

/**
 * The user's relay configuration (NIP-65 10002, NIP-17 10050, Blossom 10063) plus outbox routing:
 * other authors' relay lists are discovered from indexer relays and cached.
 */
@Singleton
public class RelayRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
) {
    private val _relayList = MutableStateFlow<Event?>(null)
    private val _dmRelayList = MutableStateFlow<Event?>(null)
    private val _blossomList = MutableStateFlow<Event?>(null)

    private val _userRelays = MutableStateFlow(RelayDefaults.appRelays)
    /** The user's NIP-65 relays, or the app defaults when none are published. */
    public val userRelays: StateFlow<List<RelayEntry>> = _userRelays.asStateFlow()

    private val _dmRelays = MutableStateFlow<List<String>>(emptyList())
    public val dmRelays: StateFlow<List<String>> = _dmRelays.asStateFlow()

    private val _blossomServers = MutableStateFlow<List<String>>(emptyList())
    public val blossomServers: StateFlow<List<String>> = _blossomServers.asStateFlow()

    private val _hasPublishedRelayList = MutableStateFlow(false)
    public val hasPublishedRelayList: StateFlow<Boolean> = _hasPublishedRelayList.asStateFlow()

    private val authorCache = ConcurrentHashMap<String, AuthorRelays>()

    public val relayListEvent: Event? get() = _relayList.value
    public val dmRelayListEvent: Event? get() = _dmRelayList.value
    public val blossomListEvent: Event? get() = _blossomList.value

    public fun readRelays(): List<String> = _userRelays.value.filter { it.read }.map { it.url }.ifEmpty { RelayDefaults.appRelays.filter { it.read }.map { it.url } }

    public fun writeRelays(): List<String> = _userRelays.value.filter { it.write }.map { it.url }.ifEmpty { RelayDefaults.appRelays.filter { it.write }.map { it.url } }

    /** Relays to discover other people's metadata/relay lists. */
    public fun discoveryRelays(): List<String> = (readRelays() + RelayDefaults.indexerRelays).distinct()

    /** Own DM inbox relays, falling back to read relays. */
    public fun ownDmRelays(): List<String> = _dmRelays.value.ifEmpty { readRelays() }

    public fun reset() {
        _relayList.value = null
        _dmRelayList.value = null
        _blossomList.value = null
        _userRelays.value = RelayDefaults.appRelays
        _dmRelays.value = emptyList()
        _blossomServers.value = emptyList()
        _hasPublishedRelayList.value = false
    }

    /** Applies a (newer) list event authored by the user. */
    public fun applyOwn(event: Event) {
        when (event.kind) {
            Kinds.RELAY_LIST -> if (isNewer(event, _relayList.value)) {
                _relayList.value = event
                val entries = RelayListParser.parseNip65(event)
                _userRelays.value = entries.ifEmpty { RelayDefaults.appRelays }
                _hasPublishedRelayList.value = entries.isNotEmpty()
            }
            Kinds.DM_RELAYS -> if (isNewer(event, _dmRelayList.value)) {
                _dmRelayList.value = event
                _dmRelays.value = RelayListParser.parseDmRelays(event)
            }
            Kinds.BLOSSOM_SERVERS -> if (isNewer(event, _blossomList.value)) {
                _blossomList.value = event
                _blossomServers.value = RelayListParser.parseBlossomServers(event)
            }
        }
    }

    private fun isNewer(event: Event, current: Event?): Boolean = current == null || event.createdAt > current.createdAt ||
        (event.createdAt == current.createdAt && event.id < current.id)

    /** Loads the user's lists: cache first, then relays (user relays + indexers). */
    public suspend fun loadOwn(pubkey: String) {
        val filter = Filter(kinds = listOf(Kinds.RELAY_LIST, Kinds.DM_RELAYS, Kinds.BLOSSOM_SERVERS), authors = listOf(pubkey))
        cache.query(filter).forEach(::applyOwn)
        val fresh = network.query(discoveryRelays() + writeRelays(), listOf(filter))
        cache.insert(fresh)
        fresh.forEach(::applyOwn)
    }

    /** Relay lists of [pubkey] (cached, then network). */
    public suspend fun relaysOf(pubkey: String): AuthorRelays? {
        authorCache[pubkey]?.let { return it }
        prefetch(listOf(pubkey))
        return authorCache[pubkey]
    }

    /** Batch-loads relay lists for [pubkeys] that are not cached yet. */
    public suspend fun prefetch(pubkeys: Collection<String>) {
        val missing = pubkeys.filter { !authorCache.containsKey(it) }.distinct()
        if (missing.isEmpty()) return
        val filterFor = { authors: List<String> -> Filter(kinds = listOf(Kinds.RELAY_LIST, Kinds.DM_RELAYS), authors = authors) }
        val cached = missing.chunked(200).flatMap { cache.query(filterFor(it)) }
        absorb(cached)
        val stillMissing = missing.filter { !authorCache.containsKey(it) }
        if (stillMissing.isNotEmpty()) {
            val fresh = stillMissing.chunked(200).flatMap { network.query(discoveryRelays(), listOf(filterFor(it)), idleTimeoutMs = 2_500, maxWaitMs = 6_000) }
            cache.insert(fresh)
            absorb(fresh)
        }
        // Remember authors without lists so we don't refetch them on every screen.
        missing.forEach { authorCache.putIfAbsent(it, AuthorRelays(it, emptyList(), emptyList(), emptyList())) }
    }

    private fun absorb(events: List<Event>) {
        events.groupBy { it.pubKey }.forEach { (pk, list) ->
            val nip65 = list.filter { it.kind == Kinds.RELAY_LIST }.maxByOrNull { it.createdAt }
            val dm = list.filter { it.kind == Kinds.DM_RELAYS }.maxByOrNull { it.createdAt }
            val entries = nip65?.let(RelayListParser::parseNip65) ?: emptyList()
            val old = authorCache[pk]
            authorCache[pk] = AuthorRelays(
                pubkey = pk,
                read = entries.filter { it.read }.map { it.url }.ifEmpty { old?.read ?: emptyList() },
                write = entries.filter { it.write }.map { it.url }.ifEmpty { old?.write ?: emptyList() },
                dm = dm?.let(RelayListParser::parseDmRelays) ?: old?.dm ?: emptyList(),
            )
        }
    }

    /**
     * Outbox routing for reading [authors]: up to [perAuthor] of each author's write relays, plus the
     * user's read relays for everyone. Returns relay → authors, capped at [maxRelays] relays.
     */
    public suspend fun outboxMap(authors: Collection<String>, perAuthor: Int = 2, maxRelays: Int = 14): Map<String, List<String>> {
        prefetch(authors)
        val map = HashMap<String, MutableSet<String>>()
        val read = readRelays()
        read.forEach { r -> map.getOrPut(r) { mutableSetOf() }.addAll(authors) }
        authors.forEach { author ->
            authorCache[author]?.write?.take(perAuthor)?.forEach { r -> map.getOrPut(r) { mutableSetOf() }.add(author) }
        }
        val ranked = map.entries.sortedWith(compareByDescending<Map.Entry<String, MutableSet<String>>> { it.key in read }.thenByDescending { it.value.size })
        return ranked.take(maxRelays).associate { it.key to it.value.toList() }
    }

    /** Relays where [pubkey]'s own posts are found (their write relays + our read relays). */
    public suspend fun relaysForAuthor(pubkey: String): List<String> =
        ((relaysOf(pubkey)?.write ?: emptyList()).take(4) + readRelays()).distinct()

    /** Inbox (read) relays of [pubkeys], used when publishing events that tag them. */
    public suspend fun inboxRelaysOf(pubkeys: Collection<String>, perUser: Int = 3): List<String> {
        val subset = pubkeys.distinct().take(20)
        prefetch(subset)
        return subset.flatMap { authorCache[it]?.read?.take(perUser) ?: emptyList() }.distinct()
    }

    /** NIP-17 delivery relays for [pubkey]: their 10050 list, falling back to read relays, then defaults. */
    public suspend fun dmInboxOf(pubkey: String): List<String> {
        val relays = relaysOf(pubkey)
        return relays?.dm?.takeIf { it.isNotEmpty() }
            ?: relays?.read?.takeIf { it.isNotEmpty() }
            ?: RelayDefaults.appRelays.map { it.url }
    }
}
