package rocks.lumina.app.core.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.nostr.content.ContentToken
import rocks.lumina.app.core.nostr.content.ContentTokenizer
import rocks.lumina.app.core.nostr.model.CommentParser
import rocks.lumina.app.core.nostr.model.MuteList
import rocks.lumina.app.core.nostr.model.PictureParser
import rocks.lumina.app.core.nostr.model.ProfileParser
import rocks.lumina.app.core.nostr.model.ReactionParser
import rocks.lumina.app.core.nostr.model.RelayEntry
import rocks.lumina.app.core.nostr.model.RelayListParser

class ParsersTest {
    private val pk = "a".repeat(64)
    private val sha = "b".repeat(64)

    private fun event(kind: Int, content: String = "", vararg tags: Array<String>) =
        Event("0".repeat(64), pk, 1_700_000_000, kind, arrayOf(*tags), content, "")

    @Test
    fun validPictureIsParsed() {
        val e = event(
            20, "Sunset #Sunset",
            arrayOf("title", "Sunset"),
            arrayOf("imeta", "url https://blossom.example/$sha.jpg", "m image/jpeg", "dim 2048x1536", "x $sha", "blurhash LKO2?U%2Tw=w]~RBVZRi};RPxuwH", "alt Red sky", "fallback http://insecure/x.jpg", "fallback https://other/x.jpg"),
            arrayOf("t", "Sunset"),
            arrayOf("p", "c".repeat(64)),
            arrayOf("content-warning"),
        )
        val post = requireNotNull(PictureParser.parse(e))
        post.let {
            assertEquals("Sunset", it.title)
            assertEquals(1, it.images.size)
            assertEquals(2048, it.images[0].width)
            assertEquals(4f / 3f, requireNotNull(it.images[0].aspectRatio), 0.001f)
            assertEquals(sha, it.images[0].sha256)
            assertEquals(listOf("https://other/x.jpg"), it.images[0].fallbacks)
            assertEquals(listOf("sunset"), it.hashtags)
            assertTrue(it.isSensitive)
            assertEquals("", it.contentWarning)
        }
    }

    @Test
    fun invalidPicturesAreDropped() {
        assertNull(PictureParser.parse(event(20, "", arrayOf("imeta", "url http://insecure/a.jpg", "m image/jpeg"))))
        assertNull(PictureParser.parse(event(20, "", arrayOf("imeta", "url https://x/a.mp4", "m video/mp4"))))
        assertNull(PictureParser.parse(event(20, "")))
        assertNull(PictureParser.parse(event(1, "", arrayOf("imeta", "url https://x/a.jpg", "m image/jpeg"))))
        // Missing `m` is tolerated only with an image extension.
        assertNotNull(PictureParser.parse(event(20, "", arrayOf("imeta", "url https://x/a.webp"))))
        assertNull(PictureParser.parse(event(20, "", arrayOf("imeta", "url https://x/blob"))))
    }

    @Test
    fun badDimAndHashAreIgnored() {
        val post = PictureParser.parse(event(20, "", arrayOf("imeta", "url https://x/a.jpg", "m image/jpeg", "dim big", "x nothex")))
        assertNull(requireNotNull(post).images[0].width)
        assertNull(post.images[0].sha256)
    }

    @Test
    fun profileIsSanitised() {
        val p = requireNotNull(ProfileParser.parse(event(0, """{"name":"alice","display_name":"Alice","picture":"javascript:alert(1)","website":"alice.dev","custom":"keep"}""")))
        assertEquals("Alice", p.bestName)
        assertNull(p.picture)
        assertEquals("https://alice.dev", p.website)
        assertTrue(p.raw.containsKey("custom"))
        assertNull(ProfileParser.parse(event(0, "not json")))
    }

    @Test
    fun relayListsParse() {
        val e = event(10002, "", arrayOf("r", "wss://a.example"), arrayOf("r", "wss://b.example", "read"), arrayOf("r", "wss://c.example", "write"), arrayOf("r", "not a url"))
        val entries = RelayListParser.parseNip65(e)
        assertEquals(3, entries.size)
        assertTrue(entries[0].read && entries[0].write)
        assertTrue(entries[1].read && !entries[1].write)
        assertTrue(!entries[2].read && entries[2].write)
        val tags = RelayListParser.nip65Tags(listOf(RelayEntry("wss://x/", true, false), RelayEntry("wss://y/", true, true)))
        assertEquals(listOf("r", "wss://x/", "read"), tags[0].toList())
        assertEquals(listOf("r", "wss://y/"), tags[1].toList())
        val blossom = RelayListParser.parseBlossomServers(event(10063, "", arrayOf("server", "https://s.example/"), arrayOf("server", "http://bad")))
        assertEquals(listOf("https://s.example/"), blossom)
    }

    @Test
    fun muteListHides() {
        val mutes = MuteList.fromTags(listOf(listOf("p", pk), listOf("word", "Spam"), listOf("t", "NSFW")))
        assertTrue(mutes.hides(pk))
        assertTrue(mutes.hides("d".repeat(64), "this is SPAM"))
        assertTrue(mutes.hides("d".repeat(64), tags = listOf("nsfw")))
        assertFalse(mutes.hides("d".repeat(64), "hello"))
        val json = MuteList.privateTagsToJson(listOf(listOf("p", pk)))
        assertEquals(listOf(listOf("p", pk)), MuteList.parsePrivateTags(json))
    }

    @Test
    fun commentsAndReactions() {
        val c = requireNotNull(CommentParser.parse(event(1111, "nice", arrayOf("E", "root"), arrayOf("K", "20"), arrayOf("e", "root"), arrayOf("k", "20"))))
        assertTrue(c.isTopLevel)
        val reply = requireNotNull(CommentParser.parse(event(1111, "yes", arrayOf("E", "root"), arrayOf("e", "parent"), arrayOf("k", "1111"))))
        assertFalse(reply.isTopLevel)
        assertTrue(requireNotNull(ReactionParser.parse(event(7, "+", arrayOf("e", "x")))).isLike)
        assertFalse(requireNotNull(ReactionParser.parse(event(7, "-", arrayOf("e", "x")))).isLike)
    }

    @Test
    fun tokenizerFindsLinksTagsAndMentions() {
        val npub = NostrLinks.npub(pk)
        val tokens = ContentTokenizer.tokenize("Hi nostr:$npub see https://x.example/a. #Nostr :wave: <b>", mapOf("wave" to "https://e/w.png"))
        assertTrue(tokens.any { it is ContentToken.Mention && (it.link as NostrLink.Profile).pubkey == pk })
        assertTrue(tokens.any { it is ContentToken.Link && it.url == "https://x.example/a" })
        assertTrue(tokens.any { it is ContentToken.Hashtag && it.tag == "nostr" })
        assertTrue(tokens.any { it is ContentToken.Emoji && it.shortcode == "wave" })
        // HTML stays plain text.
        assertTrue(tokens.last() is ContentToken.Text && (tokens.last() as ContentToken.Text).text.contains("<b>"))
        assertEquals(listOf("nostr"), ContentTokenizer.hashtags("#Nostr and #nostr"))
        assertTrue(ContentTokenizer.tokenize("http://insecure.example").all { it is ContentToken.Text })
    }

    @Test
    fun nostrLinksRoundTrip() {
        val npub = NostrLinks.npub(pk)
        assertEquals(NostrLink.Profile(pk), NostrLinks.parse("nostr:$npub"))
        assertEquals(pk, NostrLinks.pubkeyOf(pk))
        val nevent = NostrLinks.nevent("e".repeat(64), pk, 20, listOf("wss://relay.example/"))
        val parsed = NostrLinks.parse(nevent) as NostrLink.Event
        assertEquals("e".repeat(64), parsed.id)
        assertEquals(pk, parsed.author)
        assertEquals(20, parsed.kind)
        assertNull(NostrLinks.parse("nsec1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"))
        assertNull(NostrLinks.parse("hello"))
    }

    @Test
    fun urlSafety() {
        assertEquals("https://a.b/c", UrlSafety.httpsOrNull(" https://a.b/c "))
        assertNull(UrlSafety.httpsOrNull("http://a.b"))
        assertNull(UrlSafety.httpsOrNull("javascript:alert(1)"))
        assertEquals("https://example.com", UrlSafety.websiteOrNull("http://example.com"))
    }
}
