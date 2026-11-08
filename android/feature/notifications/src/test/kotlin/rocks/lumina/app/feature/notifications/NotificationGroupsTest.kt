package rocks.lumina.app.feature.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.data.NotificationGroup
import rocks.lumina.app.core.data.NotificationType

class NotificationGroupsTest {
    private fun group(key: String, at: Long, actors: List<String>, unread: Boolean = false, sats: Long = 0) =
        NotificationGroup(key, NotificationType.LIKE, actors, at, "post", null, sats, unread)

    @Test
    fun `merge combines groups that straddle pages`() {
        val merged = NotificationGroups.merge(
            listOf(group("like:p", 200, listOf("a", "b"), unread = true, sats = 10), group("follow:1", 150, listOf("c"))),
            listOf(group("like:p", 100, listOf("b", "d"), sats = 5), group("like:q", 90, listOf("e"))),
        )
        assertEquals(listOf("like:p", "follow:1", "like:q"), merged.map { it.key })
        val likes = merged.first()
        assertEquals(listOf("a", "b", "d"), likes.actors)
        assertEquals(200L, likes.createdAt)
        assertEquals(15L, likes.zapSats)
        assertTrue(likes.unread)
    }

    @Test
    fun `sections split unread from read keeping order`() {
        val (fresh, earlier) = NotificationGroups.sections(
            listOf(group("1", 3, listOf("a"), unread = true), group("2", 2, listOf("a")), group("3", 1, listOf("a"), unread = true)),
        )
        assertEquals(listOf("1", "3"), fresh.map { it.key })
        assertEquals(listOf("2"), earlier.map { it.key })
    }

    @Test
    fun `follow targets point at the actor profile`() {
        val actor = "f".repeat(64)
        val uri = NotificationTargets.uriFor(NotificationType.FOLLOW, null, actor, "e".repeat(64))
        assertTrue(uri.startsWith("nostr:npub1"))
    }

    @Test
    fun `post targets are nevent links`() {
        val uri = NotificationTargets.uriFor(NotificationType.LIKE, "1".repeat(64), "f".repeat(64), "e".repeat(64))
        assertTrue(uri.startsWith("nostr:nevent1"))
    }
}
