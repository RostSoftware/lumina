package rocks.lumina.app.feature.create

import rocks.lumina.app.core.nostr.content.ContentTokenizer

/** One finished upload as it goes into an `imeta` tag. */
data class UploadedImage(
    val url: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sha256: String,
    val size: Long,
    val blurhash: String?,
    val alt: String = "",
)

/** Everything the user entered for a kind 20 post. */
data class PictureDraft(
    val caption: String,
    val images: List<UploadedImage>,
    val extraHashtags: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val location: String = "",
    val contentWarning: Boolean = false,
    val contentWarningReason: String = "",
)

/**
 * Builds kind 20 (NIP-68) content and tags exactly like LUMINA web (`lumina-web/docs/nostr-protocol.md`), so a post
 * made on Android renders identically everywhere. Pure, unit-tested.
 */
object PictureTags {
    const val TITLE_MAX = 80
    const val LOCATION_MAX = 100
    const val CAPTION_MAX = 2200
    const val HASHTAG_MAX = 64
    const val CLIENT = "LUMINA"

    private val hashtagInText = Regex("#[\\p{L}\\p{N}_]+")
    private val validHashtag = Regex("^[\\p{L}\\p{N}_]{1,$HASHTAG_MAX}$")

    /** The event `content`: the trimmed caption. */
    fun content(caption: String): String = caption.trim()

    /** First caption line without hashtags, at most 80 chars, or "Photo". */
    fun title(caption: String): String {
        val firstLine = caption.trim().lineSequence().firstOrNull().orEmpty()
        return hashtagInText.replace(firstLine, "").trim().take(TITLE_MAX).trim().ifEmpty { "Photo" }
    }

    /** Lowercased hashtags written in the caption (without `#`). */
    fun captionHashtags(caption: String): List<String> =
        ContentTokenizer.hashtags(caption).map { it.lowercase() }.filter { validHashtag.matches(it) }

    /** Normalises a manually typed hashtag (`#Foo` → `foo`), or null when invalid. */
    fun normalizeHashtag(input: String): String? =
        input.trim().removePrefix("#").lowercase().takeIf { validHashtag.matches(it) }

    /** Caption hashtags followed by manual ones, deduplicated, in order. */
    fun allHashtags(caption: String, extra: List<String>): List<String> =
        (captionHashtags(caption) + extra.mapNotNull(::normalizeHashtag)).distinct()

    fun imeta(image: UploadedImage): Array<String> = buildList {
        add("imeta")
        add("url ${image.url}")
        add("m ${image.mimeType}")
        add("dim ${image.width}x${image.height}")
        add("x ${image.sha256}")
        add("size ${image.size}")
        image.blurhash?.takeIf { it.isNotBlank() }?.let { add("blurhash $it") }
        image.alt.trim().takeIf { it.isNotEmpty() }?.let { add("alt $it") }
    }.toTypedArray()

    fun build(draft: PictureDraft): List<Array<String>> {
        require(draft.images.isNotEmpty()) { "a picture post needs at least one image" }
        val tags = ArrayList<Array<String>>()
        tags += arrayOf("title", title(draft.caption))
        draft.images.forEach { tags += imeta(it) }
        draft.images.map { it.mimeType }.distinct().forEach { tags += arrayOf("m", it) }
        draft.images.forEach { tags += arrayOf("x", it.sha256) }
        allHashtags(draft.caption, draft.extraHashtags).forEach { tags += arrayOf("t", it) }
        draft.people.distinct().forEach { tags += arrayOf("p", it) }
        draft.location.trim().take(LOCATION_MAX).trim().takeIf { it.isNotEmpty() }?.let { tags += arrayOf("location", it) }
        if (draft.contentWarning) {
            val reason = draft.contentWarningReason.trim()
            tags += if (reason.isNotEmpty()) arrayOf("content-warning", reason) else arrayOf("content-warning")
        }
        tags += arrayOf("client", CLIENT)
        return tags
    }
}
