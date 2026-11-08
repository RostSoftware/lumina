package rocks.lumina.app.feature.profile

import rocks.lumina.app.core.media.CropRect
import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.ui.util.formatCount

/** Editable kind 0 fields, keyed by their JSON name. */
enum class ProfileField(val key: String) {
    Name("name"),
    DisplayName("display_name"),
    About("about"),
    Picture("picture"),
    Banner("banner"),
    Website("website"),
    Nip05("nip05"),
    Lud16("lud16"),
}

/** Why a field can't be saved. */
enum class FieldError { NotHttps, InvalidAddress }

/** Pure helpers for the profile feature (unit tested). */
internal object ProfileLogic {
    private val addressRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val nip05Regex = Regex("^([^@\\s]+@)?[^@\\s/]+\\.[^@\\s/]+$")

    /** "1.2K" or "500+" when the relay limit was hit. */
    fun countLabel(count: Int, capped: Boolean): String = formatCount(count) + if (capped) "+" else ""

    /** Centered crop with the given width/height [aspect] for an image of [width]x[height]. */
    fun centerCrop(width: Int, height: Int, aspect: Float): CropRect {
        if (width <= 0 || height <= 0 || aspect <= 0f) return CropRect.Full
        val imageAspect = width.toFloat() / height
        return if (imageAspect > aspect) {
            val w = aspect / imageAspect
            val left = (1f - w) / 2f
            CropRect(left, 0f, left + w, 1f)
        } else {
            val h = imageAspect / aspect
            val top = (1f - h) / 2f
            CropRect(0f, top, 1f, top + h)
        }
    }

    /** Validates a single (trimmed) field value; blank is always allowed (removes the key). */
    fun validate(field: ProfileField, value: String): FieldError? {
        val v = value.trim()
        if (v.isEmpty()) return null
        return when (field) {
            ProfileField.Picture, ProfileField.Banner -> if (UrlSafety.httpsOrNull(v) == null) FieldError.NotHttps else null
            ProfileField.Website -> when {
                v.startsWith("http://", ignoreCase = true) -> FieldError.NotHttps
                UrlSafety.websiteOrNull(v) == null -> FieldError.NotHttps
                else -> null
            }
            ProfileField.Lud16 -> if (addressRegex.matches(v)) null else FieldError.InvalidAddress
            ProfileField.Nip05 -> if (nip05Regex.matches(v)) null else FieldError.InvalidAddress
            ProfileField.Name, ProfileField.DisplayName, ProfileField.About -> null
        }
    }

    /** Normalised value written to the event (websites get an https scheme). */
    fun normalize(field: ProfileField, value: String): String? {
        val v = if (field == ProfileField.About) value.trimEnd() else value.trim()
        if (v.isBlank()) return null
        return when (field) {
            ProfileField.Website -> UrlSafety.websiteOrNull(v) ?: v
            ProfileField.Lud16, ProfileField.Nip05 -> v.lowercase()
            else -> v
        }
    }

    /**
     * Only changed fields are sent, so values another client wrote meanwhile (and unknown keys) survive —
     * the repository merges these into the newest kind 0. `null` removes a key.
     */
    fun changes(original: Map<ProfileField, String>, edited: Map<ProfileField, String>): Map<String, String?> =
        ProfileField.entries.mapNotNull { f ->
            val before = original[f].orEmpty()
            val after = edited[f].orEmpty()
            if (normalize(f, before) == normalize(f, after)) null else f.key to normalize(f, after)
        }.toMap()

    /** Display form of a website (no scheme, no trailing slash). */
    fun displayUrl(url: String): String = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
}
