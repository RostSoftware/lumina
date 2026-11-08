package rocks.lumina.app.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginInputsTest {
    private val pk = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

    @Test
    fun bunkerUriNeedsPubkeyAndRelay() {
        assertTrue(LoginInputs.isBunkerUri("bunker://$pk?relay=wss%3A%2F%2Frelay.nsec.app&secret=abc"))
        assertTrue(LoginInputs.isBunkerUri("  bunker://$pk?secret=x&relay=wss://r.example  "))
        assertFalse(LoginInputs.isBunkerUri("bunker://$pk"))
        assertFalse(LoginInputs.isBunkerUri("bunker://$pk?relay="))
        assertFalse(LoginInputs.isBunkerUri("bunker://nothex?relay=wss://r"))
        assertFalse(LoginInputs.isBunkerUri("nostrconnect://$pk?relay=wss://r"))
    }

    @Test
    fun scannedContentIsCleaned() {
        assertEquals("bunker://$pk?relay=wss://r", LoginInputs.cleanScanned(" nostr:bunker://$pk?relay=wss://r\n"))
        assertNull(LoginInputs.cleanScanned("https://example.com"))
        assertNull(LoginInputs.cleanScanned(null))
    }

    @Test
    fun secretKeyFormats() {
        assertTrue(LoginInputs.looksLikeSecretKey("nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"))
        assertTrue(LoginInputs.looksLikeSecretKey(pk))
        assertFalse(LoginInputs.looksLikeSecretKey("npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6"))
        assertFalse(LoginInputs.looksLikeSecretKey("nsec1short"))
        assertFalse(LoginInputs.looksLikeSecretKey(""))
    }

    @Test
    fun profileChangesOmitBlankFields() {
        assertEquals(emptyMap<String, String?>(), LoginInputs.profileChanges("  ", ""))
        assertEquals(mapOf("name" to "Alice", "display_name" to "Alice"), LoginInputs.profileChanges(" Alice ", " "))
        assertEquals(mapOf("about" to "hi"), LoginInputs.profileChanges("", "hi"))
    }

    @Test
    fun amberPermissionsCoverSigningAndEncryption() {
        assertTrue(AmberPermissions.all.size > 20)
        assertTrue(AmberPermissions.all.any { it.kind == 20 })
    }
}
