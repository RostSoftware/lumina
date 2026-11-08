package rocks.lumina.app.feature.notifications

import rocks.lumina.app.core.data.NotificationType
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrLinks

/** Where tapping a system notification leads: a `nostr:` deep link handled by MainActivity. */
object NotificationTargets {
    /**
     * Posts open as `nostr:nevent…` (the author is [me] for interactions on own posts, the actor for
     * mentions). Follows and profile zaps open the actor's profile as `nostr:npub…`.
     */
    fun uriFor(type: NotificationType, postId: String?, actor: String, me: String): String {
        if (postId == null || type == NotificationType.FOLLOW) return "nostr:" + NostrLinks.npub(actor)
        val author = if (type == NotificationType.MENTION) actor else me
        return "nostr:" + NostrLinks.nevent(postId, author, Kinds.PICTURE)
    }
}
