package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import javax.inject.Inject
import javax.inject.Singleton
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.model.Comment
import rocks.lumina.app.core.nostr.model.CommentParser
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Reaction
import rocks.lumina.app.core.nostr.model.ReactionParser
import rocks.lumina.app.core.nostr.model.ZapParser
import rocks.lumina.app.core.nostr.model.ZapReceipt
import rocks.lumina.app.core.signer.AccountManager

/** Aggregated engagement for one post. */
public data class PostStats(
    val likes: Int = 0,
    val myReactionId: String? = null,
    val reposts: Int = 0,
    val myRepostId: String? = null,
    val comments: Int = 0,
    val zapSats: Long = 0,
    val zapCount: Int = 0,
    /** Top zappers: pubkey → total sats, highest first. */
    val topZappers: List<Pair<String, Long>> = emptyList(),
    val zappedByMe: Boolean = false,
) {
    val likedByMe: Boolean get() = myReactionId != null
    val repostedByMe: Boolean get() = myRepostId != null
}

/** Full engagement lists for the post detail "likes / reposts / zaps" sheet. */
public data class Engagement(
    val reactions: List<Reaction>,
    val reposters: List<Pair<String, Long>>,
    val zaps: List<ZapReceipt>,
)

/**
 * Post engagement: kinds 7 / 6 / 16 / 1111 / 9735 for a batch of posts are fetched in **one** combined
 * query and split client-side (root AGENTS.md "Counts").
 */
@Singleton
public class PostRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val lists: ListsRepository,
    private val accountManager: AccountManager,
) {
    private val engagementKinds = listOf(Kinds.REACTION, Kinds.REPOST, Kinds.GENERIC_REPOST, Kinds.COMMENT, Kinds.ZAP_RECEIPT)

    private fun relaysFor(posts: Collection<PicturePost>): List<String> = (relays.readRelays() + relays.discoveryRelays().take(2)).distinct()

    /** Cached stats (instant), computed from the local event cache. */
    public suspend fun cachedStats(posts: Collection<PicturePost>): Map<String, PostStats> {
        if (posts.isEmpty()) return emptyMap()
        val ids = posts.map { it.id }
        val events = ids.chunked(100).flatMap { chunk ->
            cache.query(listOf(Filter(kinds = engagementKinds, tags = mapOf("e" to chunk)), Filter(kinds = listOf(Kinds.COMMENT), tags = mapOf("E" to chunk))))
        }
        return split(ids, events)
    }

    /** Fresh stats for [posts] from relays (one combined REQ per 100 posts). */
    public suspend fun stats(posts: Collection<PicturePost>): Map<String, PostStats> {
        if (posts.isEmpty()) return emptyMap()
        val ids = posts.map { it.id }
        val events = ids.chunked(100).flatMap { chunk ->
            network.query(
                relaysFor(posts),
                listOf(
                    Filter(kinds = engagementKinds, tags = mapOf("e" to chunk), limit = 3000),
                    Filter(kinds = listOf(Kinds.COMMENT), tags = mapOf("E" to chunk), limit = 1000),
                ),
                idleTimeoutMs = 3_000,
                maxWaitMs = 8_000,
            )
        }
        cache.insert(events)
        val cached = ids.chunked(100).flatMap { cache.query(Filter(kinds = engagementKinds, tags = mapOf("e" to it))) }
        return split(ids, (events + cached).distinctBy { it.id })
    }

    private fun split(ids: List<String>, events: List<Event>): Map<String, PostStats> {
        val me = accountManager.activePubkey
        val mutes = lists.mutes.value
        val visible = events.filterNot { mutes.hides(it.pubKey) }
        val deleted = visible.filter { it.kind == Kinds.DELETION }.flatMap { d -> d.tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] } }.toSet()
        return ids.associateWith { id ->
            fun refs(e: Event, tag: String) = e.tags.any { it.size > 1 && it[0] == tag && it[1] == id }
            val reactions = visible.filter { it.kind == Kinds.REACTION && refs(it, "e") && it.id !in deleted }
                .mapNotNull(ReactionParser::parse).filter { it.isLike }
            val likers = reactions.groupBy { it.pubkey }
            val reposts = visible.filter { (it.kind == Kinds.REPOST || it.kind == Kinds.GENERIC_REPOST) && refs(it, "e") && it.id !in deleted }
            val comments = visible.filter { it.kind == Kinds.COMMENT && refs(it, "E") && it.id !in deleted }.distinctBy { it.id }
            val zaps = visible.filter { it.kind == Kinds.ZAP_RECEIPT && refs(it, "e") }.mapNotNull(ZapParser::parse)
            val zappers = zaps.filter { it.sender != null }.groupBy { it.sender.orEmpty() }
                .map { (k, v) -> k to v.sumOf { it.amountSats } }.sortedByDescending { it.second }
            PostStats(
                likes = likers.size,
                myReactionId = me?.let { likers[it]?.maxByOrNull { r -> r.createdAt }?.id },
                reposts = reposts.map { it.pubKey }.distinct().size,
                myRepostId = me?.let { m -> reposts.filter { it.pubKey == m }.maxByOrNull { it.createdAt }?.id },
                comments = comments.size,
                zapSats = zaps.sumOf { it.amountSats },
                zapCount = zaps.size,
                topZappers = zappers.take(3),
                zappedByMe = me != null && zaps.any { it.sender == me },
            )
        }
    }

    /** NIP-22 comments on a post (root `E`), oldest first, muted users removed, deletions honoured. */
    public suspend fun comments(post: PicturePost): List<Comment> {
        val filter = Filter(kinds = listOf(Kinds.COMMENT), tags = mapOf("E" to listOf(post.id)), limit = 500)
        val targets = (relays.readRelays() + relays.relaysForAuthor(post.pubkey)).distinct()
        val cached = cache.query(filter)
        val fresh = network.query(targets, listOf(filter))
        cache.insert(fresh)
        val all = (cached + fresh).distinctBy { it.id }
        val deletions = network.query(targets, listOf(Filter(kinds = listOf(Kinds.DELETION), tags = mapOf("e" to all.map { it.id }.take(400)))))
            .filter { d -> all.any { it.pubKey == d.pubKey } }
        val deletedIds = deletions.flatMap { d -> d.tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }.filter { id -> all.any { it.id == id && it.pubKey == d.pubKey } } }.toSet()
        val mutes = lists.mutes.value
        return all.filter { it.id !in deletedIds }
            .mapNotNull(CommentParser::parse)
            .filterNot { mutes.hides(it.pubkey, it.content) }
            .sortedBy { it.createdAt }
    }

    public suspend fun cachedComments(post: PicturePost): List<Comment> {
        val mutes = lists.mutes.value
        return cache.query(Filter(kinds = listOf(Kinds.COMMENT), tags = mapOf("E" to listOf(post.id))))
            .mapNotNull(CommentParser::parse).filterNot { mutes.hides(it.pubkey, it.content) }.sortedBy { it.createdAt }
    }

    /** Lists of likers / reposters / zaps for a post. */
    public suspend fun engagement(post: PicturePost): Engagement {
        val filter = Filter(kinds = listOf(Kinds.REACTION, Kinds.REPOST, Kinds.GENERIC_REPOST, Kinds.ZAP_RECEIPT), tags = mapOf("e" to listOf(post.id)), limit = 1000)
        val events = (cache.query(filter) + network.query((relays.readRelays() + relays.relaysForAuthor(post.pubkey)).distinct(), listOf(filter))).distinctBy { it.id }
        cache.insert(events)
        val mutes = lists.mutes.value
        val visible = events.filterNot { mutes.hides(it.pubKey) }
        return Engagement(
            reactions = visible.filter { it.kind == Kinds.REACTION }.mapNotNull(ReactionParser::parse).filter { it.isLike }
                .groupBy { it.pubkey }.mapNotNull { it.value.maxByOrNull { r -> r.createdAt } }.sortedByDescending { it.createdAt },
            reposters = visible.filter { it.kind == Kinds.REPOST || it.kind == Kinds.GENERIC_REPOST }
                .groupBy { it.pubKey }.map { (pk, list) -> pk to list.maxOf { it.createdAt } }.sortedByDescending { it.second },
            zaps = visible.filter { it.kind == Kinds.ZAP_RECEIPT }.mapNotNull(ZapParser::parse).sortedByDescending { it.amountSats },
        )
    }
}
