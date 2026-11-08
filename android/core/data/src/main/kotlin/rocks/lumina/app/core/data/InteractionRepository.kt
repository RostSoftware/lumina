package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import javax.inject.Inject
import javax.inject.Singleton
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.content.ContentTokenizer
import rocks.lumina.app.core.nostr.model.Comment
import rocks.lumina.app.core.nostr.model.PicturePost

/** NIP-56 report types. */
public enum class ReportType(public val tag: String) {
    SPAM("spam"), NUDITY("nudity"), PROFANITY("profanity"), ILLEGAL("illegal"), IMPERSONATION("impersonation"), MALWARE("malware"), OTHER("other")
}

/**
 * Writes for post interactions, producing exactly the events documented in
 * `lumina-web/docs/nostr-protocol.md` so both apps interoperate.
 */
@Singleton
public class InteractionRepository @Inject constructor(
    private val publisher: Publisher,
    private val relays: RelayRepository,
    private val cache: EventCache,
) {
    /** Like: kind 7 `+` with `e`, `p`, `k=20`. Returns the reaction event. */
    public suspend fun like(post: PicturePost, content: String = "+"): Event =
        publisher.signAndPublish(
            Kinds.REACTION,
            content,
            listOf(arrayOf("e", post.id), arrayOf("p", post.pubkey), arrayOf("k", Kinds.PICTURE.toString())),
            extraRelays = relays.relaysForAuthor(post.pubkey).take(3),
        )

    /** Unlike: kind 5 deleting the reaction. */
    public suspend fun unlike(reactionId: String): Event = delete(reactionId, Kinds.REACTION)

    /** Repost: NIP-18 generic repost kind 16 with `k=20` and the post JSON as content. */
    public suspend fun repost(post: PicturePost): Event =
        publisher.signAndPublish(
            Kinds.GENERIC_REPOST,
            post.event.toJson(),
            listOf(arrayOf("e", post.id, relays.readRelays().firstOrNull().orEmpty()), arrayOf("p", post.pubkey), arrayOf("k", Kinds.PICTURE.toString())),
            extraRelays = relays.relaysForAuthor(post.pubkey).take(3),
        )

    public suspend fun unrepost(repostId: String): Event = delete(repostId, Kinds.GENERIC_REPOST)

    /** NIP-22 comment on [post], or a reply to [parent]. `nostr:npub` mentions add `p` tags. */
    public suspend fun comment(post: PicturePost, text: String, parent: Comment? = null): Event {
        val tags = ArrayList<Array<String>>()
        tags += arrayOf("E", post.id, "", post.pubkey)
        tags += arrayOf("K", Kinds.PICTURE.toString())
        tags += arrayOf("P", post.pubkey)
        if (parent == null) {
            tags += arrayOf("e", post.id, "", post.pubkey)
            tags += arrayOf("k", Kinds.PICTURE.toString())
            tags += arrayOf("p", post.pubkey)
        } else {
            tags += arrayOf("e", parent.id, "", parent.pubkey)
            tags += arrayOf("k", Kinds.COMMENT.toString())
            tags += arrayOf("p", parent.pubkey)
        }
        val already = tags.filter { it[0] == "p" }.map { it[1] }.toMutableSet()
        ContentTokenizer.mentionedPubkeys(text).forEach { pk -> if (already.add(pk)) tags += arrayOf("p", pk) }
        ContentTokenizer.hashtags(text).forEach { tags += arrayOf("t", it) }
        return publisher.signAndPublish(Kinds.COMMENT, text, tags, extraRelays = relays.relaysForAuthor(post.pubkey).take(3))
    }

    /** NIP-09 deletion of an own post. Also removes it from the local cache. */
    public suspend fun deletePost(post: PicturePost): Event {
        val event = delete(post.id, Kinds.PICTURE)
        cache.delete(Filter(ids = listOf(post.id)))
        return event
    }

    public suspend fun deleteComment(comment: Comment): Event = delete(comment.id, Kinds.COMMENT)

    private suspend fun delete(id: String, kind: Int): Event =
        publisher.signAndPublish(Kinds.DELETION, "", listOf(arrayOf("e", id), arrayOf("k", kind.toString())), notifyTagged = false)

    /** NIP-56 report of a post (and its author) or of a user only. */
    public suspend fun report(pubkey: String, postId: String?, type: ReportType, details: String): Event {
        val tags = ArrayList<Array<String>>()
        if (postId != null) tags += arrayOf("e", postId, type.tag)
        tags += arrayOf("p", pubkey, type.tag)
        return publisher.signAndPublish(Kinds.REPORT, details.trim(), tags, notifyTagged = false)
    }
}
