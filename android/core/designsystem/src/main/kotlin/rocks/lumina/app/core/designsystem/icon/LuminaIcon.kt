package rocks.lumina.app.core.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/** A Lucide glyph: a list of SVG path strings in a 24x24 viewBox. */
public class LucideGlyph(public val name: String, public val paths: List<String>)

/** Default Lucide stroke width used across LUMINA (root AGENTS.md: 1.75). */
public const val ICON_STROKE: Float = 1.75f

/** Builds (and caches) Compose [ImageVector]s from Lucide glyphs. */
public object LuminaIcons {
    private val cache = HashMap<String, ImageVector>()

    public fun vector(glyph: LucideGlyph, filled: Boolean = false, strokeWidth: Float = ICON_STROKE): ImageVector {
        val key = "${glyph.name}:$filled:$strokeWidth"
        synchronized(cache) {
            cache[key]?.let { return it }
        }
        val builder = ImageVector.Builder(
            name = "lucide-${glyph.name}",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
        glyph.paths.forEach { d ->
            builder.addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        val vector = builder.build()
        synchronized(cache) { cache[key] = vector }
        return vector
    }
}

/**
 * Renders a Lucide glyph. Active/selected state = the same glyph [filled].
 * Pass a [contentDescription] for standalone icons; `null` when a parent is labelled.
 */
@Composable
public fun LuminaIcon(
    glyph: LucideGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LuminaTheme.colors.foreground,
    size: Dp = 24.dp,
    strokeWidth: Float = ICON_STROKE,
) {
    val vector = remember(glyph, filled, strokeWidth) { LuminaIcons.vector(glyph, filled, strokeWidth) }
    Icon(imageVector = vector, contentDescription = contentDescription, modifier = modifier.size(size), tint = tint)
}

/** Bottom-bar "Create" icon: when active, a filled square with a knocked-out plus (matches web). */
@Composable
public fun CreateIcon(active: Boolean, contentDescription: String?, modifier: Modifier = Modifier) {
    if (!active) {
        LuminaIcon(LucideGlyphs.SquarePlus, contentDescription, modifier)
        return
    }
    Box(modifier.size(24.dp), contentAlignment = Alignment.Center) {
        LuminaIcon(LucideGlyphs.Square, contentDescription, filled = true)
        LuminaIcon(LucideGlyphs.Plus, null, size = 12.dp, strokeWidth = 2.5f, tint = LuminaTheme.colors.background)
    }
}
