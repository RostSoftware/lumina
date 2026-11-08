package rocks.lumina.app.feature.messages

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.data.DmMessage

class ChatItemsTest {
    private val me = "me"
    private val peer = "peer"
    private val day1 = LocalDate.of(2026, 1, 1).atStartOfDay().toEpochSecond(ZoneOffset.UTC)

    private fun msg(id: String, sender: String, at: Long) = DmMessage(id, peer, sender, "x", at, legacy = false)

    @Test
    fun `inserts day separators and sorts oldest first`() {
        val items = ChatItems.build(
            listOf(msg("c", peer, day1 + 86_400 + 10), msg("a", me, day1 + 10), msg("b", peer, day1 + 20)),
            me,
            ZoneOffset.UTC,
        )
        assertEquals(listOf("day:2026-01-01", "msg:a", "msg:b", "day:2026-01-02", "msg:c"), items.map { it.key })
        assertTrue((items[1] as ChatItem.Bubble).own)
        assertFalse((items[2] as ChatItem.Bubble).own)
    }

    @Test
    fun `groups consecutive messages from the same sender`() {
        val items = ChatItems.build(
            listOf(msg("a", me, day1 + 10), msg("b", me, day1 + 20), msg("c", peer, day1 + 30), msg("d", peer, day1 + 3_000)),
            me,
            ZoneOffset.UTC,
        ).filterIsInstance<ChatItem.Bubble>()
        assertEquals(listOf(true, false, false, false), items.map { it.groupedWithNext })
    }

    @Test
    fun `logged out marks nothing as own`() {
        val items = ChatItems.build(listOf(msg("a", me, day1)), null, ZoneOffset.UTC).filterIsInstance<ChatItem.Bubble>()
        assertFalse(items.single().own)
    }
}
