package rocks.lumina.app.core.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirmDetailed
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Thin, LUMINA-shaped facade over the single shared Quartz [NostrClient].
 *
 * - [query]: one-shot multi-relay REQ, deduplicated, **signature-verified**, bounded by a wall clock.
 * - [stream]: live subscription as a cold [Flow]; the REQ is closed when the collector is cancelled.
 * - [publish]: send an EVENT and collect the relays that answered `OK true`.
 */
@Singleton
public class NostrNetwork @Inject constructor(
    public val client: NostrClient,
) {
    public suspend fun query(
        relays: Collection<String>,
        filters: List<Filter>,
        idleTimeoutMs: Long = 4_000,
        maxWaitMs: Long = 10_000,
    ): List<Event> = queryNormalized(normalize(relays), filters, idleTimeoutMs, maxWaitMs)

    public suspend fun queryNormalized(
        relays: Set<NormalizedRelayUrl>,
        filters: List<Filter>,
        idleTimeoutMs: Long = 4_000,
        maxWaitMs: Long = 10_000,
    ): List<Event> = withContext(Dispatchers.IO) {
        if (relays.isEmpty() || filters.isEmpty()) return@withContext emptyList()
        val seen = ConcurrentHashMap.newKeySet<String>()
        val collected = CopyOnWriteArrayList<Event>()
        withTimeoutOrNull(maxWaitMs) {
            client.fetchAllWithHooks(
                filters = relays.associateWith { filters },
                idleTimeoutMs = idleTimeoutMs,
            ) { _, event ->
                if (seen.add(event.id) && event.verify()) {
                    collected.add(event)
                    true
                } else {
                    false
                }
            }
        }
        collected.toList()
    }

    public fun stream(relays: Collection<String>, filters: List<Filter>): Flow<Event> {
        val normalized = normalize(relays)
        return callbackFlow {
            if (normalized.isEmpty() || filters.isEmpty()) {
                awaitClose { }
                return@callbackFlow
            }
            val subId = newSubId()
            val seen = ConcurrentHashMap.newKeySet<String>()
            client.subscribe(
                subId = subId,
                filters = normalized.associateWith { filters },
                listener = object : SubscriptionListener {
                    override suspend fun onEvent(
                        event: Event,
                        isLive: Boolean,
                        relay: NormalizedRelayUrl,
                        forFilters: List<Filter>?,
                    ) {
                        if (seen.add(event.id) && event.verify()) trySend(event)
                    }
                },
            )
            awaitClose { client.unsubscribe(subId) }
        }.flowOn(Dispatchers.IO)
    }

    /** Publishes [event]; returns the set of relays that accepted it (empty = nobody did). */
    public suspend fun publish(event: Event, relays: Collection<String>, timeoutSeconds: Long = 10): Set<String> =
        withContext(Dispatchers.IO) {
            val normalized = normalize(relays)
            if (normalized.isEmpty()) return@withContext emptySet()
            client.publishAndConfirmDetailed(event, normalized, timeoutSeconds)
                .filterValues { it }
                .keys
                .map { it.url }
                .toSet()
        }

    public companion object {
        public fun normalize(relays: Collection<String>): Set<NormalizedRelayUrl> =
            relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) }.toSet()

        public fun normalizeOrNull(url: String): String? = RelayUrlNormalizer.normalizeOrNull(url.trim())?.url
    }
}
