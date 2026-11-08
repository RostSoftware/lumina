package rocks.lumina.app.feature.create

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PictureTagsTest {
    private val sha1 = "a".repeat(64)
    private val sha2 = "b".repeat(64)
    private val img1 = UploadedImage("https://blossom.example/$sha1.jpg", "image/jpeg", 2048, 1536, sha1, 482113, "LKO2?U%2Tw=w]~RBVZRi};RPxuwH", alt = "A red sky")
    private val img2 = UploadedImage("https://blossom.example/$sha2.webp", "image/webp", 1080, 1080, sha2, 1000, null)
    private val img3 = img1.copy(url = "https://x.example/c.jpg", sha256 = "c".repeat(64), alt = "  ")

    private fun List<Array<String>>.asLists() = map { it.toList() }

    @Test
    fun `builds tags in web order`() {
        val tags = PictureTags.build(
            PictureDraft(
                caption = "  Sunset over the bay #Sunset\nsecond line #sf  ",
                images = listOf(img1, img2, img3),
                extraHashtags = listOf("#Photography", "sunset"),
                people = listOf("p1", "p2", "p1"),
                location = "  San Francisco ",
                contentWarning = true,
                contentWarningReason = " nudity ",
            ),
        ).asLists()
        val expected = listOf(
            listOf("title", "Sunset over the bay"),
            listOf("imeta", "url ${img1.url}", "m image/jpeg", "dim 2048x1536", "x $sha1", "size 482113", "blurhash ${img1.blurhash}", "alt A red sky"),
            listOf("imeta", "url ${img2.url}", "m image/webp", "dim 1080x1080", "x $sha2", "size 1000"),
            listOf("imeta", "url ${img3.url}", "m image/jpeg", "dim 2048x1536", "x ${img3.sha256}", "size 482113", "blurhash ${img1.blurhash}"),
            listOf("m", "image/jpeg"),
            listOf("m", "image/webp"),
            listOf("x", sha1),
            listOf("x", sha2),
            listOf("x", img3.sha256),
            listOf("t", "sunset"),
            listOf("t", "sf"),
            listOf("t", "photography"),
            listOf("p", "p1"),
            listOf("p", "p2"),
            listOf("location", "San Francisco"),
            listOf("content-warning", "nudity"),
            listOf("client", "LUMINA"),
        )
        assertEquals(expected, tags)
    }

    @Test
    fun `minimal post`() {
        val tags = PictureTags.build(PictureDraft(caption = "", images = listOf(img2))).asLists()
        assertEquals(
            listOf(
                listOf("title", "Photo"),
                listOf("imeta", "url ${img2.url}", "m image/webp", "dim 1080x1080", "x $sha2", "size 1000"),
                listOf("m", "image/webp"),
                listOf("x", sha2),
                listOf("client", "LUMINA"),
            ),
            tags,
        )
    }

    @Test
    fun `content warning without reason has no value`() {
        val tags = PictureTags.build(PictureDraft("hi", listOf(img2), contentWarning = true, contentWarningReason = "  ")).asLists()
        assertEquals(listOf("content-warning"), tags.first { it[0] == "content-warning" })
    }

    @Test
    fun `title strips hashtags and truncates`() {
        assertEquals("Photo", PictureTags.title("#only #tags\nmore text"))
        // Same as web: hashtags are removed in place, inner whitespace is kept.
        assertEquals("Hello  world", PictureTags.title("Hello #x world"))
        assertEquals(80, PictureTags.title("x".repeat(200)).length)
        assertEquals("Line one", PictureTags.title("\n  Line one  \nline two"))
    }

    @Test
    fun `content is trimmed caption`() {
        assertEquals("Sunset #a", PictureTags.content("  Sunset #a \n"))
    }

    @Test
    fun `location is capped at 100 chars`() {
        val tags = PictureTags.build(PictureDraft("", listOf(img2), location = "y".repeat(150))).asLists()
        assertEquals(100, tags.first { it[0] == "location" }[1].length)
    }

    @Test
    fun `hashtag normalisation`() {
        assertEquals("foo", PictureTags.normalizeHashtag(" #Foo "))
        assertNull(PictureTags.normalizeHashtag("two words"))
        assertNull(PictureTags.normalizeHashtag("#"))
        assertNull(PictureTags.normalizeHashtag("a".repeat(65)))
        assertEquals(listOf("a", "b"), PictureTags.allHashtags("#A text #b #a", listOf("B", "bad tag")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `needs an image`() {
        PictureTags.build(PictureDraft("x", emptyList()))
    }
}
