package rocks.lumina.app.core.nostr.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.UrlSafety

/** One image of a kind 20 post, from an `imeta` tag. */
@Immutable
public data class PostImage(
    val url: String,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val blurhash: String?,
    val alt: String?,
    val sha256: String?,
    val size: Long?,
    val fallbacks: List<String>,
) {
    /** width / height, or null when `dim` is missing. */
    val aspectRatio: Float?
        get() = if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
}

/** A validated NIP-68 picture post. */
@Immutable
public data class PicturePost(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val caption: String,
    val title: String?,
    val images: List<PostImage>,
    val hashtags: List<String>,
    val taggedPubkeys: List<String>,
    val location: String?,
    val geohash: String?,
    /** `null` = not sensitive. Empty string = sensitive without a reason. */
    val contentWarning: String?,
    val emoji: Map<String, String>,
    val event: Event,
) {
    val isSensitive: Boolean get() = contentWarning != null
}

/**
 * NIP-68 parser + validator, mirroring `lumina-web/src/lib/picture.ts`:
 * kind must be 20; at least one `imeta` with an https `url` and an image mime type (a missing `m` is
 * tolerated only if the URL has an image extension). Invalid events return null and are dropped.
 */
public object PictureParser {
    private val dimRegex = Regex("^(\\d+)x(\\d+)$")
    private val blurhashRegex = Regex("^[0-9A-Za-z#$%*+,\\-.:;=?@\\[\\]^_{|}~]{6,}$")
    private val imageExt = Regex("\\.(jpe?g|png|gif|webp|avif|heic|heif|bmp|svg)(\\?.*)?$", RegexOption.IGNORE_CASE)

    public fun parse(event: Event): PicturePost? {
        if (event.kind != Kinds.PICTURE) return null
        val images = event.tags.filter { it.isNotEmpty() && it[0] == "imeta" }.mapNotNull(::parseImeta)
        if (images.isEmpty()) return null
        val cw = event.tags.firstOrNull { it.isNotEmpty() && it[0] == "content-warning" }
        return PicturePost(
            id = event.id,
            pubkey = event.pubKey,
            createdAt = event.createdAt,
            caption = event.content,
            title = event.tagValue("title")?.takeIf { it.isNotBlank() },
            images = images,
            hashtags = event.tagValues("t").map { it.lowercase() }.distinct(),
            taggedPubkeys = event.tagValues("p").filter(UrlSafety::isHex64).distinct(),
            location = event.tagValue("location")?.takeIf { it.isNotBlank() },
            geohash = event.tagValue("g"),
            contentWarning = cw?.let { if (it.size > 1) it[1] else "" },
            emoji = event.customEmoji(),
            event = event,
        )
    }

    internal fun parseImeta(tag: Array<String>): PostImage? {
        val fields = HashMap<String, String>()
        val fallbacks = ArrayList<String>()
        for (i in 1 until tag.size) {
            val entry = tag[i]
            val space = entry.indexOf(' ')
            if (space <= 0) continue
            val key = entry.substring(0, space)
            val value = entry.substring(space + 1).trim()
            if (key == "fallback") {
                UrlSafety.httpsOrNull(value)?.let(fallbacks::add)
            } else if (!fields.containsKey(key)) {
                fields[key] = value
            }
        }
        val url = UrlSafety.httpsOrNull(fields["url"]) ?: return null
        val mime = fields["m"]?.lowercase()
        if (mime != null) {
            if (!mime.startsWith("image/")) return null
        } else if (!imageExt.containsMatchIn(url)) {
            return null
        }
        val dim = fields["dim"]?.let { dimRegex.matchEntire(it) }
        return PostImage(
            url = url,
            mimeType = mime,
            width = dim?.groupValues?.get(1)?.toIntOrNull(),
            height = dim?.groupValues?.get(2)?.toIntOrNull(),
            blurhash = fields["blurhash"]?.takeIf { blurhashRegex.matches(it) },
            alt = fields["alt"]?.takeIf { it.isNotBlank() },
            sha256 = fields["x"]?.lowercase()?.takeIf(UrlSafety::isHex64),
            size = fields["size"]?.toLongOrNull(),
            fallbacks = fallbacks,
        )
    }
}
