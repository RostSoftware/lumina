package rocks.lumina.app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.R
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/** Pulsing skeleton block on `surface-muted`. */
@Composable
public fun Skeleton(modifier: Modifier = Modifier, shape: Shape = LuminaShapes.control) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .alpha(alpha)
            .clip(shape)
            .background(LuminaTheme.colors.surfaceMuted),
    )
}

@Composable
public fun SkeletonCircle(size: Dp, modifier: Modifier = Modifier) {
    Skeleton(modifier.size(size), shape = CircleShape)
}

/** Friendly empty state with optional action. */
@Composable
public fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    glyph: LucideGlyph? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (glyph != null) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(LuminaTheme.colors.surfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                LuminaIcon(glyph, null, size = 32.dp)
            }
            Spacer(Modifier.height(16.dp))
        }
        Text(title, style = LuminaTheme.typography.title, color = LuminaTheme.colors.foreground, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            LuminaButton(text = actionLabel, onClick = onAction)
        }
    }
}

/** Error state with retry. */
@Composable
public fun ErrorState(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.ds_something_went_wrong),
    message: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LuminaIcon(LucideGlyphs.TriangleAlert, null, size = 32.dp, tint = LuminaTheme.colors.destructive)
        Spacer(Modifier.height(12.dp))
        Text(title, style = LuminaTheme.typography.title, color = LuminaTheme.colors.foreground, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(6.dp))
            Text(message, style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground, textAlign = TextAlign.Center)
        }
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            LuminaButton(text = stringResource(R.string.ds_retry), onClick = onRetry, style = ButtonStyle.Outline, leadingIcon = LucideGlyphs.RefreshCw)
        }
    }
}

/** Centered loading spinner with an accessible label. */
@Composable
public fun LoadingBox(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ds_loading)
    Box(modifier.fillMaxWidth().padding(24.dp).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Spinner()
    }
}
