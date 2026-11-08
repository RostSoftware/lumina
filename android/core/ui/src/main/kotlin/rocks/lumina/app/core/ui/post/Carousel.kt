package rocks.lumina.app.core.ui.post

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.ui.R

/** Aspect ratio for the carousel: the first image's `dim`, clamped like Instagram (4:5 … 1.91:1). */
public fun PicturePost.displayAspectRatio(): Float = (images.firstOrNull()?.aspectRatio ?: 1f).coerceIn(0.8f, 1.91f)

/**
 * Swipeable carousel with dot indicators, double-tap-to-like heart burst, and a tap-to-reveal overlay
 * for `content-warning` posts.
 */
@Composable
public fun MediaCarousel(
    post: PicturePost,
    authorName: String,
    revealSensitive: Boolean,
    onDoubleTap: () -> Unit,
    onTap: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { post.images.size }
    var revealed by remember(post.id) { mutableStateOf(revealSensitive || !post.isSensitive) }
    var burstKey by remember { mutableIntStateOf(0) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().aspectRatio(post.displayAspectRatio())) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val image = post.images[page]
                val description = image.alt ?: stringResource(R.string.post_image_fallback_alt, authorName)
                PostImageView(
                    image = image,
                    contentDescription = description,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!revealed) Modifier.blur(40.dp) else Modifier)
                        .pointerInput(post.id, revealed) {
                            if (revealed) {
                                detectTapGestures(
                                    onDoubleTap = {
                                        burstKey++
                                        onDoubleTap()
                                    },
                                    onTap = { onTap(page) },
                                )
                            }
                        },
                )
            }
            if (post.images.size > 1 && revealed) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "${pager.currentPage + 1}/${post.images.size}",
                        style = LuminaTheme.typography.caption,
                        color = Color.White,
                    )
                }
            }
            if (burstKey > 0) HeartBurst(burstKey, Modifier.align(Alignment.Center))
            if (!revealed) SensitiveOverlay(post.contentWarning, onReveal = { revealed = true })
        }
        if (post.images.size > 1) {
            Dots(count = post.images.size, selected = pager.currentPage, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

@Composable
public fun Dots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.post_image_of, selected + 1, count)
    Row(modifier.semantics { contentDescription = label }, horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (i == selected) LuminaTheme.colors.accent else LuminaTheme.colors.border),
            )
        }
    }
}

/** Double-tap heart: pops in (scale), holds, fades (≈250 ms ease-out per phase). */
@Composable
public fun HeartBurst(key: Int, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(0f) }
    val alpha = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(key) {
        alpha.snapTo(1f)
        scale.snapTo(0.2f)
        scale.animateTo(1.15f, tween(200, easing = FastOutSlowInEasing))
        scale.animateTo(1f, tween(100))
        scope.launch { alpha.animateTo(0f, tween(250, delayMillis = 250)) }
    }
    LuminaIcon(
        LucideGlyphs.Heart,
        contentDescription = null,
        filled = true,
        tint = Color.White,
        size = 96.dp,
        modifier = modifier.scale(scale.value).alpha(alpha.value),
    )
}

@Composable
public fun SensitiveOverlay(reason: String?, onReveal: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            LuminaIcon(LucideGlyphs.EyeOff, null, tint = Color.White, size = 32.dp)
            Text(
                stringResource(R.string.post_sensitive_title),
                style = LuminaTheme.typography.title,
                color = Color.White,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                if (reason.isNullOrBlank()) stringResource(R.string.post_sensitive_generic) else stringResource(R.string.post_sensitive_reason, reason),
                style = LuminaTheme.typography.body,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            LuminaButton(stringResource(R.string.post_reveal), onClick = onReveal, style = ButtonStyle.Secondary)
        }
    }
}
