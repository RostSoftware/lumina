package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.nostr.model.ProfileParser

/**
 * Kind 0 profiles: memory → event cache → relays, with requests from many composables batched into one
 * REQ per ~120 ms window. Also NIP-05 verification and NIP-50 user search.
 */
@Singleton
public class ProfileRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val publisher: Publisher,
    private val okHttp: OkHttpClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val flows = ConcurrentHashMap<String, MutableStateFlow<Profile?>>()
    private val requested = ConcurrentHashMap.newKeySet<String>()
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val nip05Cache = ConcurrentHashMap<String, Boolean>()

    init {
        scope.launch(Dispatchers.IO) { batchLoop() }
    }

    /** Observable profile; triggers a load the first time a pubkey is observed. */
    public fun observe(pubkey: String): StateFlow<Profile?> {
        val flow = flows.getOrPut(pubkey) { MutableStateFlow(null) }
        if (requested.add(pubkey)) queue.trySend(pubkey)
        return flow
    }

    public fun peek(pubkey: String): Profile? = flows[pubkey]?.value

    /** Loads profiles for many pubkeys at once (e.g. a feed page). */
    public fun prefetch(pubkeys: Collection<String>) {
        pubkeys.forEach { observe(it) }
    }

    private suspend fun batchLoop() {
        while (true) {
            val first = queue.receive()
            delay(BATCH_WINDOW_MS)
            val batch = mutableSetOf(first)
            while (true) {
                val next = queue.tryReceive().getOrNull() ?: break
                batch.add(next)
            }
            runCatching { load(batch.toList()) }
        }
    }

    private suspend fun load(pubkeys: List<String>) {
        pubkeys.chunked(100).forEach { chunk ->
            val filter = Filter(kinds = listOf(Kinds.METADATA), authors = chunk)
            absorb(cache.query(filter))
            val fresh = network.query(relays.discoveryRelays(), listOf(filter), idleTimeoutMs = 2_500, maxWaitMs = 7_000)
            cache.insert(fresh)
            absorb(fresh)
        }
    }

    private fun absorb(events: List<Event>) {
        events.groupBy { it.pubKey }.forEach { (pk, list) ->
            val newest = list.maxByOrNull { it.createdAt } ?: return@forEach
            val profile = ProfileParser.parse(newest) ?: return@forEach
            val flow = flows.getOrPut(pk) { MutableStateFlow(null) }
            val current = flow.value
            if (current == null || profile.createdAt >= current.createdAt) flow.value = profile
        }
    }

    /** Forces a network refresh of [pubkey]'s profile and returns the newest version. */
    public suspend fun refresh(pubkey: String): Profile? {
        val filter = Filter(kinds = listOf(Kinds.METADATA), authors = listOf(pubkey))
        val fresh = network.query((relays.discoveryRelays() + relays.relaysForAuthor(pubkey)).distinct(), listOf(filter))
        cache.insert(fresh)
        absorb(fresh)
        return flows[pubkey]?.value
    }

    /**
     * Edits the user's kind 0. Always starts from the newest kind 0 on relays and keeps unknown fields.
     * A `null` value removes the key.
     */
    public suspend fun updateProfile(pubkey: String, changes: Map<String, String?>): Profile {
        val filter = Filter(kinds = listOf(Kinds.METADATA), authors = listOf(pubkey))
        val candidates = cache.query(filter) +
            network.query((relays.writeRelays() + relays.discoveryRelays()).distinct(), listOf(filter))
        val newest = candidates.maxByOrNull { it.createdAt }
        val base = newest?.let { ProfileParser.parse(it)?.raw } ?: JsonObject(emptyMap())
        val merged = base.toMutableMap()
        changes.forEach { (key, value) ->
            if (value.isNullOrBlank()) merged.remove(key) else merged[key] = JsonPrimitive(value)
        }
        val content = JsonObject(merged.filterValues { it != JsonNull }).toString()
        val event = publisher.signAndPublish(Kinds.METADATA, content, emptyList(), extraRelays = RelayDefaults.indexerRelays)
        absorb(listOf(event))
        return ProfileParser.parse(event) ?: Profile.empty(pubkey)
    }

    /** NIP-05 check: `https://<domain>/.well-known/nostr.json?name=<local>` must map to [pubkey]. */
    public suspend fun verifyNip05(pubkey: String, nip05: String): Boolean {
        val key = "$nip05|$pubkey"
        nip05Cache[key]?.let { return it }
        val result = withContext(Dispatchers.IO) { resolveNip05(nip05) == pubkey }
        nip05Cache[key] = result
        return result
    }

    /** Resolves `name@domain` (or `domain` = `_@domain`) to a hex pubkey. */
    public suspend fun resolveNip05(nip05: String): String? = withContext(Dispatchers.IO) {
        val parts = nip05.trim().lowercase().split('@')
        val (name, domain) = when (parts.size) {
            1 -> "_" to parts[0]
            2 -> parts[0].ifEmpty { "_" } to parts[1]
            else -> return@withContext null
        }
        if (domain.isBlank() || !domain.contains('.') || domain.contains('/')) return@withContext null
        runCatching {
            val request = Request.Builder().url("https://$domain/.well-known/nostr.json?name=$name").get().build()
            okHttp.newBuilder().followRedirects(false).build().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                val names = kotlinx.serialization.json.Json.parseToJsonElement(body).let { (it as? JsonObject)?.get("names") as? JsonObject }
                (names?.get(name) as? JsonPrimitive)?.content?.lowercase()?.takeIf { it.length == 64 }
            }
        }.getOrNull()
    }

    /** User search: direct npub/hex/NIP-05 lookups plus NIP-50 search on search relays. */
    public suspend fun search(query: String, limit: Int = 20): List<Profile> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        NostrLinks.pubkeyOf(q)?.let { pk -> return listOfNotNull(refresh(pk) ?: Profile.empty(pk)) }
        val results = LinkedHashMap<String, Profile>()
        if (q.contains('@') || (q.contains('.') && !q.contains(' '))) {
            resolveNip05(q)?.let { pk -> results[pk] = refresh(pk) ?: Profile.empty(pk) }
        }
        val filter = Filter(kinds = listOf(Kinds.METADATA), search = q, limit = limit)
        val events = network.query(RelayDefaults.searchRelays, listOf(filter), idleTimeoutMs = 3_000, maxWaitMs = 8_000)
        cache.insert(events)
        absorb(events)
        events.groupBy { it.pubKey }.values.mapNotNull { list -> list.maxByOrNull { it.createdAt }?.let(ProfileParser::parse) }
            .forEach { results.putIfAbsent(it.pubkey, it) }
        // Local fallback: matching cached profiles (works offline and for relays without NIP-50).
        if (results.size < limit) {
            val lower = q.lowercase()
            flows.values.mapNotNull { it.value }.filter { p ->
                listOfNotNull(p.name, p.displayName, p.nip05).any { it.lowercase().contains(lower) }
            }.forEach { results.putIfAbsent(it.pubkey, it) }
        }
        return results.values.take(limit)
    }

    private companion object {
        const val BATCH_WINDOW_MS = 120L
    }
}
