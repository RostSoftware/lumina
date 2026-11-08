package rocks.lumina.app.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.nostr.model.RelayEntry

class SettingsLogicTest {
    /** Test normalizer: lowercases and adds a trailing slash, like Quartz's RelayUrlNormalizer. */
    private val normalize: (String) -> String? = { url -> url.lowercase().let { if (it.endsWith("/")) it else "$it/" } }

    @Test
    fun addRelayUpgradesBareHostAndRejectsDuplicates() {
        val first = ListEditing.addRelay(emptyList(), "Relay.Example.com", normalize)
        assertNull(first.error)
        assertEquals(listOf(RelayEntry("wss://relay.example.com/", read = true, write = true)), first.list)

        val dup = ListEditing.addRelay(first.list, "wss://relay.example.com", normalize)
        assertEquals(AddError.DUPLICATE, dup.error)
        assertEquals(first.list, dup.list)
    }

    @Test
    fun addRelayRejectsInvalid() {
        assertEquals(AddError.INVALID, ListEditing.addRelay(emptyList(), "https://relay.example.com", normalize).error)
        assertEquals(AddError.INVALID, ListEditing.addRelay(emptyList(), "not a relay", normalize).error)
        assertEquals(AddError.INVALID, ListEditing.addRelay(emptyList(), "wss://nodot", normalize).error)
        assertEquals(AddError.INVALID, ListEditing.addRelay(emptyList(), "   ", normalize).error)
        assertEquals(AddError.INVALID, ListEditing.addRelay(emptyList(), "wss://ok.com", { null }).error)
    }

    @Test
    fun toggleKeepsAtLeastOneMarker() {
        val list = listOf(RelayEntry("wss://a.com/", read = true, write = false))
        assertEquals(list, ListEditing.toggle(list, "wss://a.com/", read = true))
        val withWrite = ListEditing.toggle(list, "wss://a.com/", read = false)
        assertEquals(RelayEntry("wss://a.com/", read = true, write = true), withWrite.single())
        assertEquals(RelayEntry("wss://a.com/", read = false, write = true), ListEditing.toggle(withWrite, "wss://a.com/", read = true).single())
    }

    @Test
    fun writeRelayRequired() {
        assertFalse(ListEditing.hasWriteRelay(listOf(RelayEntry("wss://a.com/", read = true, write = false))))
        assertTrue(ListEditing.hasWriteRelay(listOf(RelayEntry("wss://a.com/", read = false, write = true))))
    }

    @Test
    fun serverUrlsAreHttpsOnly() {
        assertEquals("https://blossom.band/", ListEditing.serverUrl("blossom.band"))
        assertEquals("https://cdn.example.com/path/", ListEditing.serverUrl(" https://cdn.example.com/path "))
        assertNull(ListEditing.serverUrl("http://insecure.com"))
        assertNull(ListEditing.serverUrl("ftp://x.com"))
        assertNull(ListEditing.serverUrl("localhost"))
        assertNull(ListEditing.serverUrl(""))
    }

    @Test
    fun addServerDetectsDuplicatesIgnoringSlashAndCase() {
        val list = listOf("https://blossom.band/")
        assertEquals(AddError.DUPLICATE, ListEditing.addServer(list, "https://Blossom.band").error)
        assertEquals(list + "https://b.example.com/", ListEditing.addServer(list, "b.example.com").list)
    }

    @Test
    fun moveSwapsWithinBounds() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), ListEditing.move(list, 1, -1))
        assertEquals(listOf("a", "c", "b"), ListEditing.move(list, 1, 1))
        assertEquals(list, ListEditing.move(list, 0, -1))
        assertEquals(list, ListEditing.move(list, 2, 1))
    }

    @Test
    fun sameListsIgnoreUrlSpelling() {
        assertTrue(ListEditing.sameUrls(listOf("https://a.com/"), listOf("https://A.com")))
        assertFalse(ListEditing.sameUrls(listOf("https://a.com/", "https://b.com/"), listOf("https://b.com/", "https://a.com/")))
        assertTrue(
            ListEditing.sameRelays(
                listOf(RelayEntry("wss://a.com/", read = true, write = true)),
                listOf(RelayEntry("wss://a.com", read = true, write = true)),
            ),
        )
        assertFalse(
            ListEditing.sameRelays(
                listOf(RelayEntry("wss://a.com/", read = true, write = true)),
                listOf(RelayEntry("wss://a.com/", read = true, write = false)),
            ),
        )
    }

    @Test
    fun zapAmountParsing() {
        assertEquals(21L, ZapAmount.parse("21"))
        assertEquals(1000L, ZapAmount.parse("1,000"))
        assertNull(ZapAmount.parse("0"))
        assertNull(ZapAmount.parse("abc"))
        assertNull(ZapAmount.parse("10000001"))
    }

    @Test
    fun nwcDetection() {
        assertTrue(WalletInput.looksLikeNwc("nostr+walletconnect://abc?relay=wss://r.com&secret=123"))
        assertFalse(WalletInput.looksLikeNwc("nostr+walletconnect://abc?relay=wss://r.com"))
        assertFalse(WalletInput.looksLikeNwc("lnurl1xyz"))
    }
}
