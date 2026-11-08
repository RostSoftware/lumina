package rocks.lumina.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.designsystem.theme.brandGradient

/**
 * Round avatar. Only https URLs are loaded; otherwise an initial on a color derived from [seed] is shown.
 * [ring] draws a 1.5dp foreground ring (active profile tab); [storyRing] draws the brand-gradient ring.
 */
@Composable
public fun Avatar(
    url: String?,
    seed: String,
    contentDescription: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    initial: String? = null,
    ring: Boolean = false,
    storyRing: Boolean = false,
) {
    var failed by remember(url) { mutableStateOf(false) }
    val safeUrl = url?.takeIf { it.startsWith("https://") && !failed }
    val ringModifier = when {
        storyRing -> Modifier.border(2.dp, brandGradient(size.value), CircleShape).padding(3.dp)
        ring -> Modifier.border(1.5.dp, LuminaTheme.colors.foreground, CircleShape).padding(2.dp)
        else -> Modifier
    }
    Box(modifier.size(size).then(ringModifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSizeCompat()
                .clip(CircleShape)
                .background(seedColor(seed)),
            contentAlignment = Alignment.Center,
        ) {
            if (safeUrl == null) {
                val letter = (initial?.trim()?.firstOrNull() ?: seed.firstOrNull() ?: '?').uppercaseChar()
                Text(
                    text = letter.toString(),
                    color = Color.White,
                    style = LuminaTheme.typography.username.copy(fontSize = (size.value * 0.4f).sp),
                )
            } else {
                AsyncImage(
                    model = safeUrl,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    onError = { failed = true },
                    modifier = Modifier.matchParentSizeCompat(),
                )
            }
        }
    }
}

private fun Modifier.matchParentSizeCompat(): Modifier = this.fillMaxSize()

/** Deterministic muted color for placeholder avatars. */
public fun seedColor(seed: String): Color {
    val palette = listOf(
        Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEA580C), Color(0xFF0891B2),
        Color(0xFF16A34A), Color(0xFF4F46E5), Color(0xFFCA8A04), Color(0xFF64748B),
    )
    val h = seed.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }
    return palette[h % palette.size]
}
