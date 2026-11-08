package rocks.lumina.app.feature.discover

import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.PostImage

class DiscoverLogicTest {
    private fun post(n: Int, createdAt: Long = n.toLong()): PicturePost {
        val id = "%064x".format(n)
        return PicturePost(
            id, "%064x".format(0), createdAt, "", null,
            listOf(PostImage("https://x.test/$n.jpg", "image/jpeg", 1, 1, null, null, null, null, emptyList())),
            emptyList(), emptyList(), null, null, null, emptyMap(),
            Event(id, "%064x".format(0), createdAt, 20, emptyArray(), "", ""),
        )
    }

    @Test
    fun normalizeHashtag_acceptsSingleWords() {
        assertEquals("sunset", DiscoverLogic.normalizeHashtag("#Sunset "))
        assertEquals("foto_2024", DiscoverLogic.normalizeHashtag("foto_2024"))
        assertEquals("café", DiscoverLogic.normalizeHashtag("Café"))
    }

    @Test
    fun normalizeHashtag_rejectsPhrasesAndEmpty() {
        assertNull(DiscoverLogic.normalizeHashtag("two words"))
        assertNull(DiscoverLogic.normalizeHashtag("#"))
        assertNull(DiscoverLogic.normalizeHashtag(""))
        assertNull(DiscoverLogic.normalizeHashtag("npub1@x.y"))
        assertNull(DiscoverLogic.normalizeHashtag(null))
    }

    @Test
    fun appendPage_dropsDuplicates() {
        val merged = DiscoverLogic.appendPage(listOf(post(3), post(2)), listOf(post(2), post(1)))
        assertEquals(listOf(post(3).id, post(2).id, post(1).id), merged.map { it.id })
    }

    @Test
    fun prepend_sortsNewestFirst() {
        val merged = DiscoverLogic.prepend(listOf(post(5, 50)), listOf(post(4, 40), post(5, 50)))
        assertEquals(listOf(post(5).id, post(4).id), merged.map { it.id })
    }

    @Test
    fun shouldLoadMore_nearEnd() {
        assertTrue(DiscoverLogic.shouldLoadMore(lastVisibleIndex = 15, total = 20))
        assertFalse(DiscoverLogic.shouldLoadMore(lastVisibleIndex = 2, total = 20))
        assertFalse(DiscoverLogic.shouldLoadMore(lastVisibleIndex = 0, total = 0))
    }
}
