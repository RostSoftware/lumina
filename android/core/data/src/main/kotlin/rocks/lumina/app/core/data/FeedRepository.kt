package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.emitAll
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.model.PictureParser
import rocks.lumina.app.core.nostr.model.PicturePost

/** A page of posts plus the `until` cursor for the next page (null = end). */
public data class FeedPage(val posts: List<PicturePost>, val nextUntil: Long?)

/** A repost (kind 16 `k=20`) of a picture post. */
public data class RepostItem(val repostId: String, val reposter: String, val createdAt: Long, val post: PicturePost)

/** Kind 20 feeds. Cache first, then relays; muted authors/words are filtered out everywhere. */
@Singleton
public class FeedRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val lists: ListsRepository,
    private val profiles: ProfileRepository,
) {
    public fun filterMuted(posts: List<PicturePost>): List<PicturePost> {
        val mutes = lists.mutes.value
        if (mutes.isEmpty()) return posts
        return posts.filterNot { mutes.hides(it.pubkey, it.caption, it.hashtags, it.id) }
    }

    private fun parse(events: Collection<Event>): List<PicturePost> =
        events.mapNotNull(PictureParser::parse).distinctBy { it.id }.sortedByDescending { it.createdAt }

    private fun page(posts: List<PicturePost>, limit: Int): FeedPage {
        val sorted = filterMuted(posts).take(limit)
        profiles.prefetch(sorted.map { it.pubkey })
        val next = if (posts.size < limit / 2) null else posts.take(limit).minOfOrNull { it.createdAt }?.minus(1)
        return FeedPage(sorted, next)
    }

    /** Cached posts for an instant first paint. */
    public suspend fun cachedFollowing(authors: Collection<String>, limit: Int = PAGE): List<PicturePost> {
        if (authors.isEmpty()) return emptyList()
        val events = authors.chunked(500).flatMap { cache.query(Filter(kinds = listOf(Kinds.PICTURE), authors = it, limit = limit)) }
        return filterMuted(parse(events)).take(limit)
    }

    /** Following feed (outbox model: each author's write relays + the user's read relays). */
    public suspend fun following(authors: Collection<String>, until: Long?, limit: Int = PAGE): FeedPage {
        if (authors.isEmpty()) return FeedPage(emptyList(), null)
        val map = relays.outboxMap(authors)
        val results = ArrayList<Event>()
        val byRelay = map.entries.groupBy({ it.value.sorted() }, { it.key })
        byRelay.forEach { (relayAuthors, relayUrls) ->
            val filters = relayAuthors.chunked(400).map { Filter(kinds = listOf(Kinds.PICTURE), authors = it, until = until, limit = limit) }
            results += network.query(relayUrls, filters)
        }
        cache.insert(results)
        return page(parse(results), limit)
    }

    public suspend fun cachedDiscover(hashtag: String?, limit: Int = PAGE * 2): List<PicturePost> {
        val filter = Filter(kinds = listOf(Kinds.PICTURE), tags = hashtag?.let { mapOf("t" to listOf(it.lowercase())) }, limit = limit)
        return filterMuted(parse(cache.query(filter))).take(limit)
    }

    /** Global kind 20, newest first, optional `#t` filter. */
    public suspend fun discover(hashtag: String?, until: Long?, limit: Int = PAGE * 2): FeedPage {
        val tags = hashtag?.let { mapOf("t" to listOf(it.lowercase().removePrefix("#"))) }
        val filter = Filter(kinds = listOf(Kinds.PICTURE), tags = tags, until = until, limit = limit)
        val target = relays.readRelays() + (if (hashtag != null) listOf("wss://relay.nostr.band/") else emptyList())
        val events = network.query(target.distinct(), listOf(filter))
        cache.insert(events)
        return page(parse(events), limit)
    }

    /** NIP-50 search over picture posts (captions/alt text) on search relays. */
    public suspend fun search(query: String, limit: Int = 40): List<PicturePost> {
        val filter = Filter(kinds = listOf(Kinds.PICTURE), search = query, limit = limit)
        val events = network.query(rocks.lumina.app.core.nostr.RelayDefaults.searchRelays, listOf(filter))
        cache.insert(events)
        return filterMuted(parse(events))
    }

    public suspend fun cachedUserPosts(pubkey: String, limit: Int = PAGE * 2): List<PicturePost> =
        parse(cache.query(Filter(kinds = listOf(Kinds.PICTURE), authors = listOf(pubkey), limit = limit))).take(limit)

    /** A user's posts from their write relays + our read relays. */
    public suspend fun userPosts(pubkey: String, until: Long?, limit: Int = PAGE * 2): FeedPage {
        val filter = Filter(kinds = listOf(Kinds.PICTURE), authors = listOf(pubkey), until = until, limit = limit)
        val events = network.query(relays.relaysForAuthor(pubkey), listOf(filter))
        cache.insert(events)
        val posts = parse(events)
        val next = if (posts.size < limit / 2) null else posts.minOfOrNull { it.createdAt }?.minus(1)
        return FeedPage(posts.take(limit), next)
    }

    /** Posts that tag [pubkey] (`#p`). */
    public suspend fun taggedPosts(pubkey: String, until: Long?, limit: Int = PAGE * 2): FeedPage {
        val filter = Filter(kinds = listOf(Kinds.PICTURE), tags = mapOf("p" to listOf(pubkey)), until = until, limit = limit)
        val events = network.query(relays.discoveryRelays(), listOf(filter))
        cache.insert(events)
        return page(parse(events).filter { it.pubkey != pubkey || pubkey in it.taggedPubkeys }, limit)
    }

    /** Kind 16 reposts (k=20) by [pubkey]; embedded post JSON is used when valid, else fetched by id. */
    public suspend fun reposts(pubkey: String, until: Long?, limit: Int = PAGE * 2): List<RepostItem> {
        val filter = Filter(kinds = listOf(Kinds.GENERIC_REPOST), authors = listOf(pubkey), tags = mapOf("k" to listOf("20")), until = until, limit = limit)
        val reposts = network.query(relays.relaysForAuthor(pubkey), listOf(filter))
        val embedded = reposts.associate { r ->
            r.id to r.content.takeIf { it.isNotBlank() }?.let { Event.fromJsonOrNull(it) }
                ?.takeIf { e -> e.kind == Kinds.PICTURE && e.verify() }
        }
        val missingIds = reposts.filter { embedded[it.id] == null }.mapNotNull { r -> r.tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1) }
        val fetched = if (missingIds.isEmpty()) emptyList() else network.query(relays.discoveryRelays(), listOf(Filter(ids = missingIds)))
        val byId = (fetched + embedded.values.filterNotNull()).associateBy { it.id }
        cache.insert(byId.values)
        return reposts.mapNotNull { r ->
            val target = embedded[r.id] ?: r.tags.firstOrNull { it.size > 1 && it[0] == "e" }?.get(1)?.let { byId[it] }
            target?.let(PictureParser::parse)?.let { RepostItem(r.id, r.pubKey, r.createdAt, it) }
        }.distinctBy { it.post.id }.sortedByDescending { it.createdAt }.filterNot { lists.mutes.value.hides(it.post.pubkey, it.post.caption, it.post.hashtags) }
    }

    /** Live stream of new posts (for the "new posts" pill). */
    public fun newPosts(authors: Collection<String>?, hashtag: String?, since: Long = TimeUtils.now()): Flow<PicturePost> {
        if (authors != null && authors.isEmpty()) return emptyFlow()
        return flow {
            val (relayUrls, filter) = if (authors != null) {
                relays.readRelays() to Filter(kinds = listOf(Kinds.PICTURE), authors = authors.toList(), since = since)
            } else {
                relays.readRelays() to Filter(kinds = listOf(Kinds.PICTURE), tags = hashtag?.let { mapOf("t" to listOf(it)) }, since = since)
            }
            emitAll(network.stream(relayUrls, listOf(filter)))
        }.mapNotNull(PictureParser::parse)
            .filter { !lists.mutes.value.hides(it.pubkey, it.caption, it.hashtags, it.id) }
    }

    public suspend fun cachedPost(id: String): PicturePost? = cache.query(Filter(ids = listOf(id))).firstNotNullOfOrNull(PictureParser::parse)

    /** One post by id: cache, then hints + author's relays + read relays. */
    public suspend fun post(id: String, relayHints: List<String> = emptyList(), author: String? = null): PicturePost? {
        val filter = Filter(ids = listOf(id))
        val targets = (relayHints + (author?.let { relays.relaysForAuthor(it) } ?: emptyList()) + relays.discoveryRelays()).distinct()
        val events = network.query(targets, listOf(filter), maxWaitMs = 8_000)
        cache.insert(events)
        return events.firstNotNullOfOrNull(PictureParser::parse) ?: cachedPost(id)
    }

    /** Several posts by id (cache + relays). */
    public suspend fun posts(ids: Collection<String>): List<PicturePost> {
        if (ids.isEmpty()) return emptyList()
        val cached = parse(cache.query(Filter(ids = ids.toList())))
        val missing = ids - cached.map { it.id }.toSet()
        val fetched = if (missing.isEmpty()) emptyList() else {
            val events = network.query(relays.discoveryRelays(), listOf(Filter(ids = missing.toList())))
            cache.insert(events)
            parse(events)
        }
        return (cached + fetched).distinctBy { it.id }
    }

    public companion object {
        public const val PAGE: Int = 20
    }
}
