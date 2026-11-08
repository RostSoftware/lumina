package rocks.lumina.app.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.lumina.app.core.media.CropRect

class ProfileLogicTest {
    @Test
    fun centerCrop_landscapeToSquare() {
        val crop = ProfileLogic.centerCrop(2000, 1000, 1f)
        assertEquals(0.25f, crop.left, 1e-4f)
        assertEquals(0.75f, crop.right, 1e-4f)
        assertEquals(0f, crop.top, 1e-4f)
        assertEquals(1f, crop.bottom, 1e-4f)
    }

    @Test
    fun centerCrop_portraitToBanner() {
        val crop = ProfileLogic.centerCrop(1000, 2000, 3f)
        // image aspect 0.5, banner 3 → height fraction 0.5/3
        assertEquals(1f / 6f, crop.bottom - crop.top, 1e-4f)
        assertEquals(0.5f, (crop.top + crop.bottom) / 2f, 1e-4f)
        assertEquals(0f, crop.left, 1e-4f)
    }

    @Test
    fun centerCrop_invalidSize() {
        assertEquals(CropRect.Full, ProfileLogic.centerCrop(0, 10, 1f))
    }

    @Test
    fun validate_urls() {
        assertNull(ProfileLogic.validate(ProfileField.Picture, ""))
        assertNull(ProfileLogic.validate(ProfileField.Picture, "https://cdn.example/a.jpg"))
        assertEquals(FieldError.NotHttps, ProfileLogic.validate(ProfileField.Picture, "http://cdn.example/a.jpg"))
        assertEquals(FieldError.NotHttps, ProfileLogic.validate(ProfileField.Banner, "javascript:alert(1)"))
        assertNull(ProfileLogic.validate(ProfileField.Website, "alice.example"))
        assertEquals(FieldError.NotHttps, ProfileLogic.validate(ProfileField.Website, "http://alice.example"))
    }

    @Test
    fun validate_addresses() {
        assertNull(ProfileLogic.validate(ProfileField.Lud16, "alice@getalby.com"))
        assertEquals(FieldError.InvalidAddress, ProfileLogic.validate(ProfileField.Lud16, "alice"))
        assertNull(ProfileLogic.validate(ProfileField.Nip05, "example.com"))
        assertNull(ProfileLogic.validate(ProfileField.Nip05, "_@example.com"))
        assertEquals(FieldError.InvalidAddress, ProfileLogic.validate(ProfileField.Nip05, "bad address"))
    }

    @Test
    fun changes_onlyChangedKeys_blankRemoves() {
        val original = mapOf(ProfileField.Name to "alice", ProfileField.About to "hi", ProfileField.Website to "https://a.example")
        val edited = original + mapOf(ProfileField.About to "", ProfileField.DisplayName to "Alice ", ProfileField.Website to "a.example")
        val changes = ProfileLogic.changes(original, edited)
        assertEquals(mapOf("about" to null, "display_name" to "Alice"), changes)
    }

    @Test
    fun changes_websiteNormalised() {
        val changes = ProfileLogic.changes(emptyMap(), mapOf(ProfileField.Website to "alice.example"))
        assertEquals("https://alice.example", changes["website"])
    }

    @Test
    fun countLabel_capped() {
        assertEquals("500+", ProfileLogic.countLabel(500, true))
        assertEquals("1.2K", ProfileLogic.countLabel(1200, false))
        assertTrue(ProfileLogic.displayUrl("https://a.example/").let { it == "a.example" })
    }
}
