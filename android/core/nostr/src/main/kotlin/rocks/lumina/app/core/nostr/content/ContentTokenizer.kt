package rocks.lumina.app.core.nostr.content

import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.UrlSafety

/** A piece of user-generated text. Rendering never interprets content as HTML. */
public sealed interface ContentToken {
    public data class Text(val text: String) : ContentToken
    public data class Link(val url: String, val display: String) : ContentToken
    public data class Hashtag(val tag: String, val display: String) : ContentToken
    public data class Mention(val link: NostrLink, val raw: String) : ContentToken
    public data class Emoji(val shortcode: String, val url: String) : ContentToken
}

/** Splits captions, bios and comments into safe tokens: https links, `nostr:` mentions, hashtags, custom emoji. */
public object ContentTokenizer {
    private val pattern = Regex(
        "(https?://[^\\s<>\"]+)" +
            "|((?:nostr:)?(?:npub1|nprofile1|note1|nevent1|naddr1)[qpzry9x8gf2tvdw0s3jn54khce6mua7l]+)" +
            "|(?<=^|[\\s(])(#[\\p{L}\\p{N}_]+)" +
            "|(:[a-zA-Z0-9_-]+:)",
        RegexOption.IGNORE_CASE,
    )
    private val trailingPunctuation = charArrayOf('.', ',', '!', '?', ')', ';', ':', '\'', '"')

    public fun tokenize(text: String, emoji: Map<String, String> = emptyMap()): List<ContentToken> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<ContentToken>()
        var last = 0
        fun pushText(s: String) {
            if (s.isEmpty()) return
            val prev = out.lastOrNull()
            if (prev is ContentToken.Text) out[out.size - 1] = ContentToken.Text(prev.text + s) else out.add(ContentToken.Text(s))
        }
        for (m in pattern.findAll(text)) {
            val start = m.range.first
            var end = m.range.last + 1
            val urlGroup = m.groups[1]?.value
            val nip19Group = m.groups[2]?.value
            val tagGroup = m.groups[3]?.value
            val emojiGroup = m.groups[4]?.value
            val token: ContentToken? = when {
                urlGroup != null -> {
                    val trimmed = urlGroup.trimEnd(*trailingPunctuation)
                    end = start + trimmed.length
                    UrlSafety.httpsOrNull(trimmed)?.let { ContentToken.Link(it, it.removePrefix("https://").trimEnd('/')) }
                }
                nip19Group != null -> NostrLinks.parse(nip19Group)?.let { ContentToken.Mention(it, nip19Group) }
                tagGroup != null -> ContentToken.Hashtag(tagGroup.drop(1).lowercase(), tagGroup)
                emojiGroup != null -> emoji[emojiGroup.trim(':')]?.let { ContentToken.Emoji(emojiGroup.trim(':'), it) }
                else -> null
            }
            if (token == null) continue
            pushText(text.substring(last, start))
            out.add(token)
            last = end
        }
        pushText(text.substring(last))
        return out
    }

    /** Lowercased hashtags written in [text] (without `#`). */
    public fun hashtags(text: String): List<String> = tokenize(text).filterIsInstance<ContentToken.Hashtag>().map { it.tag }.distinct()

    /** Pubkeys mentioned with `nostr:npub…` / `nostr:nprofile…`. */
    public fun mentionedPubkeys(text: String): List<String> = tokenize(text)
        .filterIsInstance<ContentToken.Mention>()
        .mapNotNull { (it.link as? NostrLink.Profile)?.pubkey }
        .distinct()
}
