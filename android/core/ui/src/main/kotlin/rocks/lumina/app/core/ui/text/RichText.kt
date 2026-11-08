package rocks.lumina.app.core.ui.text

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import coil3.compose.AsyncImage
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.content.ContentToken
import rocks.lumina.app.core.nostr.content.ContentTokenizer
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.user.rememberProfile

/** Callbacks for interactive tokens in user text. */
public class RichTextActions(
    public val onMention: (NostrLink) -> Unit = {},
    public val onHashtag: (String) -> Unit = {},
)

/**
 * Safe renderer for captions, bios and comments: https links, `nostr:` mentions (shown as @name),
 * hashtags and NIP-30 custom emoji. Content is never interpreted as HTML.
 */
@Composable
public fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LuminaTheme.typography.body,
    emoji: Map<String, String> = emptyMap(),
    actions: RichTextActions = RichTextActions(),
    maxLines: Int = Int.MAX_VALUE,
    prefix: AnnotatedString? = null,
) {
    val tokens = remember(text, emoji) { ContentTokenizer.tokenize(text, emoji) }
    val colors = LuminaTheme.colors
    val uriHandler = LocalUriHandler.current
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent))
    val mentionNames = tokens.filterIsInstance<ContentToken.Mention>().associate { token ->
        token.raw to ((token.link as? NostrLink.Profile)?.let { mentionName(it.pubkey) } ?: token.raw.removePrefix("nostr:").take(16) + "…")
    }
    val inline = HashMap<String, InlineTextContent>()
    val annotated = buildAnnotatedString {
        if (prefix != null) append(prefix)
        tokens.forEach { token ->
            when (token) {
                is ContentToken.Text -> append(token.text)
                is ContentToken.Link -> withLink(LinkAnnotation.Clickable(token.url, linkStyle) { runCatching { uriHandler.openUri(token.url) } }) {
                    append(token.display)
                }
                is ContentToken.Hashtag -> withLink(LinkAnnotation.Clickable("#${token.tag}", linkStyle) { actions.onHashtag(token.tag) }) {
                    append(token.display)
                }
                is ContentToken.Mention -> withLink(LinkAnnotation.Clickable(token.raw, linkStyle) { actions.onMention(token.link) }) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(mentionNames[token.raw].orEmpty()) }
                }
                is ContentToken.Emoji -> {
                    val id = "emoji:${token.shortcode}"
                    inline[id] = InlineTextContent(Placeholder(1.2.em, 1.2.em, PlaceholderVerticalAlign.Center)) {
                        AsyncImage(model = token.url, contentDescription = ":${token.shortcode}:")
                    }
                    appendInlineContent(id, ":${token.shortcode}:")
                }
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = colors.foreground),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        inlineContent = inline,
    )
}

@Composable
private fun mentionName(pubkey: String): String {
    val profile by rememberProfile(pubkey)
    return "@" + (profile?.bestName ?: Profile.shortNpub(pubkey))
}
