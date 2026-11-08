package rocks.lumina.app.core.data

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.nostr.model.PictureParser
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.ZapParser

public enum class NotificationType { LIKE, COMMENT, REPOST, ZAP, MENTION, FOLLOW }

/** A single raw notification derived from one event. */
public data class RawNotification(
    val eventId: String,
    val type: NotificationType,
    val actor: String,
    val createdAt: Long,
    val postId: String?,
    val text: String?,
    val zapSats: Long,
    val reactionContent: String? = null,
)

/** Instagram-style grouped notification ("alice and 4 others liked your post"). */
public data class NotificationGroup(
    val key: String,
    val type: NotificationType,
    /** Distinct actors, newest first. */
    val actors: List<String>,
    val createdAt: Long,
    val postId: String?,
    val text: String?,
    val zapSats: Long,
    val unread: Boolean,
)

public data class NotificationsPage(
    val groups: List<NotificationGroup>,
    val posts: Map<String, PicturePost>,
    val nextUntil: Long?,
)

/** Events tagging the user (`#p`): kinds 1111, 7, 16/6, 9735, 3 (new followers) and 20 (tagged in a post). */
@Singleton
public class NotificationRepository @Inject constructor(
    private val network: NostrNetwork,
    private val cache: EventCache,
    private val relays: RelayRepository,
    private val lists: ListsRepository,
    private val feed: FeedRepository,
    private val settings: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _unread = MutableStateFlow(0)
    /** Number of unread notification events since the per-account "last seen". */
    public val unread: StateFlow<Int> = _unread.asStateFlow()
    private var watchJob: Job? = null

    private fun filter(pubkey: String, since: Long? = null, until: Long? = null, limit: Int = 100) = Filter(
        kinds = listOf(Kinds.REACTION, Kinds.REPOST, Kinds.GENERIC_REPOST, Kinds.COMMENT, Kinds.ZAP_RECEIPT, Kinds.CONTACTS, Kinds.PICTURE),
        tags = mapOf("p" to listOf(pubkey)),
        since = since,
        until = until,
        limit = limit,
    )

    /** Classifies an event into a notification for [me], or null when it isn't one. */
    public fun classify(event: Event, me: String): RawNotification? {
        if (event.pubKey == me && event.kind != Kinds.ZAP_RECEIPT) return null
        fun tag(name: String) = event.tags.lastOrNull { it.size > 1 && it[0] == name }?.get(1)
        return when (event.kind) {
            Kinds.REACTION -> {
                val k = tag("k")
                if (k != null && k != "20") return null
                if (event.content == "-") return null
                RawNotification(event.id, NotificationType.LIKE, event.pubKey, event.createdAt, tag("e"), null, 0, event.content)
            }
            Kinds.REPOST, Kinds.GENERIC_REPOST -> {
                val k = tag("k")
                if (event.kind == Kinds.GENERIC_REPOST && k != null && k != "20") return null
                RawNotification(event.id, NotificationType.REPOST, event.pubKey, event.createdAt, tag("e"), null, 0)
            }
            Kinds.COMMENT -> {
                val rootKind = event.tags.firstOrNull { it.size > 1 && it[0] == "K" }?.get(1)
                if (rootKind != null && rootKind != "20") return null
                val root = event.tags.firstOrNull { it.size > 1 && it[0] == "E" }?.get(1)
                RawNotification(event.id, NotificationType.COMMENT, event.pubKey, event.createdAt, root, event.content, 0)
            }
            Kinds.ZAP_RECEIPT -> {
                val zap = ZapParser.parse(event) ?: return null
                if (zap.recipient != me) return null
                val sender = zap.sender ?: return null
                if (sender == me) return null
                RawNotification(event.id, NotificationType.ZAP, sender, zap.createdAt, zap.targetId, zap.comment.ifBlank { null }, zap.amountSats)
            }
            Kinds.CONTACTS -> RawNotification(event.id, NotificationType.FOLLOW, event.pubKey, event.createdAt, null, null, 0)
            Kinds.PICTURE -> if (PictureParser.parse(event) != null) {
                RawNotification(event.id, NotificationType.MENTION, event.pubKey, event.createdAt, event.id, event.content.take(140), 0)
            } else {
                null
            }
            else -> null
        }
    }

    /** Raw notifications newer than [since] (used by the background worker). */
    public suspend fun fetchRaw(pubkey: String, since: Long): List<RawNotification> {
        val events = network.query(relays.readRelays(), listOf(filter(pubkey, since = since + 1)))
        val mutes = lists.mutes.value
        return events.mapNotNull { classify(it, pubkey) }.filterNot { mutes.hides(it.actor, it.text.orEmpty()) }
            .distinctBy { it.type to (it.actor to it.postId) }
    }

    /** One page of grouped notifications. */
    public suspend fun page(pubkey: String, until: Long?): NotificationsPage {
        val f = filter(pubkey, until = until)
        val events = (network.query(relays.readRelays(), listOf(f)) + if (until == null) cache.query(f) else emptyList()).distinctBy { it.id }
        cache.insert(events)
        val mutes = lists.mutes.value
        val raw = events.mapNotNull { classify(it, pubkey) }.filterNot { mutes.hides(it.actor, it.text.orEmpty()) }
        val postIds = raw.mapNotNull { it.postId }.distinct()
        val posts = feed.posts(postIds).filter { it.pubkey == pubkey || it.id in raw.filter { r -> r.type == NotificationType.MENTION }.map { r -> r.postId } }
            .associateBy { it.id }
        // Hide interactions whose target isn't one of the user's kind 20 posts (zaps on profiles are kept).
        val visible = raw.filter { r ->
            when (r.type) {
                NotificationType.FOLLOW -> true
                NotificationType.ZAP -> r.postId == null || posts.containsKey(r.postId)
                else -> r.postId != null && posts.containsKey(r.postId)
            }
        }
        val lastSeen = settings.settingsFor(pubkey).notificationsLastSeen
        val next = if (events.size < 50) null else events.minOfOrNull { it.createdAt }?.minus(1)
        return NotificationsPage(group(visible, lastSeen), posts, next)
    }

    public fun group(raw: List<RawNotification>, lastSeen: Long): List<NotificationGroup> {
        val groups = LinkedHashMap<String, MutableList<RawNotification>>()
        raw.sortedByDescending { it.createdAt }.forEach { r ->
            val key = when (r.type) {
                NotificationType.LIKE, NotificationType.REPOST, NotificationType.ZAP -> "${r.type}:${r.postId}"
                NotificationType.FOLLOW -> "${r.type}:${r.createdAt / 86_400}"
                else -> "${r.type}:${r.eventId}"
            }
            groups.getOrPut(key) { mutableListOf() }.add(r)
        }
        return groups.map { (key, items) ->
            val newest = items.first()
            NotificationGroup(
                key = key,
                type = newest.type,
                actors = items.map { it.actor }.distinct(),
                createdAt = newest.createdAt,
                postId = newest.postId,
                text = newest.text,
                zapSats = items.sumOf { it.zapSats },
                unread = newest.createdAt > lastSeen,
            )
        }.sortedByDescending { it.createdAt }
    }

    /** Starts watching for new notifications since "last seen" to drive the nav badge. */
    public fun startUnreadWatch(pubkey: String) {
        watchJob?.cancel()
        _unread.value = 0
        watchJob = scope.launch {
            var lastSeen = settings.settingsFor(pubkey).notificationsLastSeen
            if (lastSeen == 0L) {
                lastSeen = TimeUtils.now() - 7 * 86_400
            }
            val seen = HashSet<String>()
            network.stream(relays.readRelays(), listOf(filter(pubkey, since = lastSeen + 1))).collect { event ->
                val current = settings.settingsFor(pubkey).notificationsLastSeen
                val n = classify(event, pubkey) ?: return@collect
                if (n.createdAt <= current || lists.mutes.value.hides(n.actor)) return@collect
                if (seen.add(event.id)) _unread.value = seen.size
            }
        }
    }

    public fun stopUnreadWatch() {
        watchJob?.cancel()
        watchJob = null
        _unread.value = 0
    }

    public suspend fun markSeen(pubkey: String) {
        settings.markNotificationsSeen(pubkey, TimeUtils.now())
        _unread.value = 0
        startUnreadWatch(pubkey)
    }
}
