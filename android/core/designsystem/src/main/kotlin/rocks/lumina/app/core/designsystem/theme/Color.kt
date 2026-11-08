package rocks.lumina.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset

/**
 * LUMINA design tokens. These values are the single source of truth defined in the root `AGENTS.md`
 * ("Shared Design System") and must stay identical to `lumina-web/src/index.css`.
 */
@Immutable
public data class LuminaColors(
    val background: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val foreground: Color,
    val mutedForeground: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val like: Color,
    val zap: Color,
    val destructive: Color,
    val isDark: Boolean,
)

public val LightLuminaColors: LuminaColors = LuminaColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceMuted = Color(0xFFF5F5F5),
    foreground = Color(0xFF0A0A0A),
    mutedForeground = Color(0xFF737373),
    border = Color(0xFFE5E5E5),
    primary = Color(0xFF0A0A0A),
    onPrimary = Color(0xFFFFFFFF),
    accent = Color(0xFF7C3AED),
    like = Color(0xFFFF3040),
    zap = Color(0xFFF59E0B),
    destructive = Color(0xFFDC2626),
    isDark = false,
)

public val DarkLuminaColors: LuminaColors = LuminaColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0A0A0A),
    surfaceMuted = Color(0xFF171717),
    foreground = Color(0xFFFAFAFA),
    mutedForeground = Color(0xFFA3A3A3),
    border = Color(0xFF262626),
    primary = Color(0xFFFAFAFA),
    onPrimary = Color(0xFF0A0A0A),
    accent = Color(0xFFA78BFA),
    like = Color(0xFFFF3040),
    zap = Color(0xFFFBBF24),
    destructive = Color(0xFFF87171),
    isDark = true,
)

/** Brand gradient (#F59E0B → #EC4899 → #7C3AED at 45°). Only for the wordmark, app icon and avatar ring. */
public fun brandGradient(size: Float = 100f): Brush = Brush.linearGradient(
    colors = listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF7C3AED)),
    start = Offset(0f, size),
    end = Offset(size, 0f),
)
