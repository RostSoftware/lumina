package rocks.lumina.app.core.ui.post

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.data.PostStats
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.text.RichText
import rocks.lumina.app.core.ui.text.RichTextActions
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.user.rememberProfile
import rocks.lumina.app.core.ui.util.formatCount
import rocks.lumina.app.core.ui.util.formatSats
import rocks.lumina.app.core.ui.util.relativeTime

/** Everything a post card can do. Screens wire these to `PostActionsViewModel` and navigation. */
public class PostCallbacks(
    public val onOpenProfile: (String) -> Unit = {},
    public val onOpenPost: (PicturePost) -> Unit = {},
    public val onLike: (PicturePost) -> Unit = {},
    public val onDoubleTapLike: (PicturePost) -> Unit = {},
    public val onComment: (PicturePost) -> Unit = {},
    public val onRepost: (PicturePost) -> Unit = {},
    public val onZap: (PicturePost) -> Unit = {},
    public val onShare: (PicturePost) -> Unit = {},
    public val onBookmark: (PicturePost) -> Unit = {},
    public val onMore: (PicturePost) -> Unit = {},
    public val onOpenMedia: (PicturePost, Int) -> Unit = { _, _ -> },
    public val onHashtag: (String) -> Unit = {},
    public val onMention: (NostrLink) -> Unit = {},
    public val onShowEngagement: (PicturePost) -> Unit = {},
)

/** Instagram-style post card: header, carousel, action row, counts, caption, comments link, time. */
@Composable
public fun PostCard(
    post: PicturePost,
    stats: PostStats?,
    bookmarked: Boolean,
    revealSensitive: Boolean,
    callbacks: PostCallbacks,
    modifier: Modifier = Modifier,
    repostedBy: String? = null,
    expandCaption: Boolean = false,
    showCommentsLink: Boolean = true,
) {
    val profile by rememberProfile(post.pubkey)
    val authorName = profile?.bestName ?: Profile.shortNpub(post.pubkey)
    val richActions = RichTextActions(onMention = callbacks.onMention, onHashtag = callbacks.onHashtag)
    Column(modifier.fillMaxWidth().background(LuminaTheme.colors.background)) {
        if (repostedBy != null) RepostedBy(repostedBy, callbacks.onOpenProfile)
        PostHeader(post, profile, callbacks)
        MediaCarousel(
            post = post,
            authorName = authorName,
            revealSensitive = revealSensitive,
            onDoubleTap = { callbacks.onDoubleTapLike(post) },
            onTap = { index -> callbacks.onOpenMedia(post, index) },
        )
        ActionRow(post, stats, bookmarked, callbacks)
        Column(Modifier.padding(horizontal = LuminaDimens.gutter)) {
            val likes = stats?.likes ?: 0
            if (likes > 0) {
                Text(
                    text = if (likes == 1) stringResource(R.string.post_one_like) else stringResource(R.string.post_likes, formatCount(likes)),
                    style = LuminaTheme.typography.count,
                    color = LuminaTheme.colors.foreground,
                    modifier = Modifier.tapTarget(label = null, onClick = { callbacks.onShowEngagement(post) }),
                )
            }
            if (stats != null && stats.zapSats > 0) ZapSummary(stats) { callbacks.onShowEngagement(post) }
            if (post.caption.isNotBlank()) {
                var expanded by remember(post.id) { mutableStateOf(expandCaption) }
                val prefix = buildAnnotatedString { withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(authorName) }; append("  ") }
                Box(Modifier.padding(top = 4.dp).then(if (!expanded) Modifier.tapTarget(label = null, onClick = { expanded = true }) else Modifier)) {
                    RichText(
                        text = post.caption,
                        emoji = post.emoji,
                        actions = richActions,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        prefix = prefix,
                    )
                }
            }
            val comments = stats?.comments ?: 0
            if (showCommentsLink && comments > 0) {
                Text(
                    text = if (comments == 1) stringResource(R.string.post_view_one_comment) else stringResource(R.string.post_view_comments, comments),
                    style = LuminaTheme.typography.body,
                    color = LuminaTheme.colors.mutedForeground,
                    modifier = Modifier.padding(top = 4.dp).tapTarget(label = null, onClick = { callbacks.onOpenPost(post) }),
                )
            }
            Text(
                relativeTime(post.createdAt),
                style = LuminaTheme.typography.caption,
                color = LuminaTheme.colors.mutedForeground,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun RepostedBy(pubkey: String, onOpenProfile: (String) -> Unit) {
    val profile by rememberProfile(pubkey)
    Row(
        Modifier.fillMaxWidth().padding(start = LuminaDimens.gutter, top = 8.dp).tapTarget(label = null, onClick = { onOpenProfile(pubkey) }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuminaIcon(LucideGlyphs.Repeat2, null, size = 14.dp, tint = LuminaTheme.colors.mutedForeground)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(R.string.post_reposted_by, profile?.bestName ?: Profile.shortNpub(pubkey)),
            style = LuminaTheme.typography.caption,
            color = LuminaTheme.colors.mutedForeground,
        )
    }
}

@Composable
private fun PostHeader(post: PicturePost, profile: Profile?, callbacks: PostCallbacks) {
    Row(
        Modifier.fillMaxWidth().padding(start = LuminaDimens.postHeaderPadding, end = 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).tapTarget(role = Role.Button, label = profile?.bestName, onClick = { callbacks.onOpenProfile(post.pubkey) }).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(post.pubkey, LuminaDimens.avatarPost, profile = profile)
            Spacer(Modifier.width(10.dp))
            Column {
                UserName(post.pubkey, profile = profile)
                if (post.location != null) {
                    Text(post.location.orEmpty(), style = LuminaTheme.typography.caption, color = LuminaTheme.colors.foreground, maxLines = 1)
                }
            }
        }
        LuminaIconButton(LucideGlyphs.Ellipsis, stringResource(R.string.post_more), onClick = { callbacks.onMore(post) })
    }
}

@Composable
private fun ActionRow(post: PicturePost, stats: PostStats?, bookmarked: Boolean, callbacks: PostCallbacks) {
    val liked = stats?.likedByMe == true
    val scale by animateFloatAsState(if (liked) 1f else 0.92f, tween(200), label = "likePop")
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LuminaIconButton(
            LucideGlyphs.Heart,
            stringResource(if (liked) R.string.post_unlike else R.string.post_like),
            onClick = { callbacks.onLike(post) },
            filled = liked,
            tint = if (liked) LuminaTheme.colors.like else LuminaTheme.colors.foreground,
            modifier = Modifier.scale(if (liked) scale.coerceAtLeast(1f) else 1f),
        )
        LuminaIconButton(LucideGlyphs.MessageCircle, stringResource(R.string.post_comment), onClick = { callbacks.onComment(post) })
        val reposted = stats?.repostedByMe == true
        LuminaIconButton(
            LucideGlyphs.Repeat2,
            stringResource(if (reposted) R.string.post_undo_repost else R.string.post_repost),
            onClick = { callbacks.onRepost(post) },
            tint = if (reposted) LuminaTheme.colors.accent else LuminaTheme.colors.foreground,
        )
        LuminaIconButton(
            LucideGlyphs.Zap,
            stringResource(R.string.post_zap),
            onClick = { callbacks.onZap(post) },
            filled = stats?.zappedByMe == true,
            tint = if (stats?.zappedByMe == true) LuminaTheme.colors.zap else LuminaTheme.colors.foreground,
        )
        LuminaIconButton(LucideGlyphs.Share2, stringResource(R.string.post_share), onClick = { callbacks.onShare(post) })
        Spacer(Modifier.weight(1f))
        LuminaIconButton(
            LucideGlyphs.Bookmark,
            stringResource(if (bookmarked) R.string.post_unbookmark else R.string.post_bookmark),
            onClick = { callbacks.onBookmark(post) },
            filled = bookmarked,
        )
    }
}

@Composable
private fun ZapSummary(stats: PostStats, onClick: () -> Unit) {
    Row(
        Modifier.padding(top = 2.dp).tapTarget(label = null, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LuminaIcon(LucideGlyphs.Zap, null, size = 14.dp, filled = true, tint = LuminaTheme.colors.zap)
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.post_zapped, formatSats(stats.zapSats)), style = LuminaTheme.typography.count, color = LuminaTheme.colors.zap)
        if (stats.topZappers.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Box {
                stats.topZappers.forEachIndexed { i, (pk, _) ->
                    ProfileAvatar(pk, 18.dp, modifier = Modifier.offset(x = (i * 12).dp))
                }
            }
        }
    }
}

/** Skeleton matching [PostCard]'s layout. */
@Composable
public fun PostCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(LuminaDimens.postHeaderPadding), verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(LuminaDimens.avatarPost)
            Spacer(Modifier.width(10.dp))
            Skeleton(Modifier.width(120.dp).height(12.dp))
        }
        Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), shape = androidx.compose.ui.graphics.RectangleShape)
        Column(Modifier.padding(LuminaDimens.gutter)) {
            Skeleton(Modifier.width(160.dp).height(12.dp))
            Spacer(Modifier.height(8.dp))
            Skeleton(Modifier.fillMaxWidth(0.8f).height(12.dp))
        }
    }
}

/** Square grid cell (Discover / Profile): first image, multi-photo indicator, blurred if sensitive. */
@Composable
public fun PostGridCell(post: PicturePost, revealSensitive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val profile by rememberProfile(post.pubkey)
    val image = post.images.first()
    val description = image.alt ?: stringResource(R.string.post_image_fallback_alt, profile?.bestName ?: Profile.shortNpub(post.pubkey))
    Box(modifier.aspectRatio(1f).tapTarget(label = description, onClick = onClick)) {
        PostImageView(
            image = image,
            contentDescription = description,
            modifier = Modifier.fillMaxSize().then(if (post.isSensitive && !revealSensitive) Modifier.blur(24.dp) else Modifier),
            blurhashSize = 16,
        )
        if (post.isSensitive && !revealSensitive) {
            LuminaIcon(LucideGlyphs.EyeOff, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        if (post.images.size > 1) {
            LuminaIcon(
                LucideGlyphs.Copy,
                stringResource(R.string.post_multiple),
                tint = Color.White,
                size = 18.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}
