package rocks.lumina.app.feature.post

import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rocks.lumina.app.core.nostr.model.Comment

class CommentThreadTest {
    private val dummy = Event("0".repeat(64), "1".repeat(64), 0, 1111, emptyArray(), "", "")

    private fun c(id: String, parent: String?, kind: String, at: Long) =
        Comment(id, "pk", at, "text $id", "root", parent, kind, emptyMap(), dummy)

    @Test
    fun repliesAreGroupedUnderTopLevelAncestor() {
        val list = listOf(
            c("a", "root", "20", 1),
            c("b", "a", "1111", 2),
            c("c", "b", "1111", 3),
            c("d", "root", "20", 4),
        )
        val threads = buildThreads(list)
        assertEquals(listOf("a", "d"), threads.map { it.comment.id })
        assertEquals(listOf("b", "c"), threads[0].replies.map { it.id })
    }

    @Test
    fun orphanRepliesBecomeTopLevel() {
        val threads = buildThreads(listOf(c("x", "missing", "1111", 1)))
        assertEquals("x", threads.single().comment.id)
    }

    @Test
    fun mentionQueryDetection() {
        assertEquals("ali", mentionQueryAt("hi @ali", 7))
        assertNull(mentionQueryAt("mail@ali", 8))
        assertNull(mentionQueryAt("hi @ali bob", 11))
        val (text, cursor) = replaceMention("hi @ali", 7, "nostr:npub1x")
        assertEquals("hi nostr:npub1x ", text)
        assertEquals(text.length, cursor)
    }
}
