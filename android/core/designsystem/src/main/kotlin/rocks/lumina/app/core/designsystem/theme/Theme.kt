package rocks.lumina.app.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Light / dark / follow system. Persisted by the settings repository. */
public enum class ThemeMode { SYSTEM, LIGHT, DARK }

public val LocalLuminaColors: androidx.compose.runtime.ProvidableCompositionLocal<LuminaColors> =
    staticCompositionLocalOf { LightLuminaColors }
public val LocalLuminaTypography: androidx.compose.runtime.ProvidableCompositionLocal<LuminaTypography> =
    staticCompositionLocalOf { LuminaTypography() }

private fun LuminaColors.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = surfaceMuted,
        onPrimaryContainer = foreground,
        secondary = accent,
        onSecondary = onPrimary,
        secondaryContainer = surfaceMuted,
        onSecondaryContainer = foreground,
        tertiary = accent,
        onTertiary = onPrimary,
        background = background,
        onBackground = foreground,
        surface = surface,
        onSurface = foreground,
        surfaceVariant = surfaceMuted,
        onSurfaceVariant = mutedForeground,
        surfaceContainerLowest = background,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceMuted,
        surfaceBright = surface,
        surfaceDim = surface,
        surfaceTint = surface,
        inverseSurface = foreground,
        inverseOnSurface = background,
        inversePrimary = accent,
        outline = border,
        outlineVariant = border,
        error = destructive,
        onError = onPrimary,
        errorContainer = surfaceMuted,
        onErrorContainer = destructive,
        scrim = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
    )
}

private fun LuminaTypography.toMaterial(): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Inter),
        displayMedium = t.displayMedium.copy(fontFamily = Inter),
        displaySmall = t.displaySmall.copy(fontFamily = Inter),
        headlineLarge = t.headlineLarge.copy(fontFamily = Inter),
        headlineMedium = t.headlineMedium.copy(fontFamily = Inter),
        headlineSmall = headline,
        titleLarge = headline,
        titleMedium = title,
        titleSmall = username,
        bodyLarge = body.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
        bodyMedium = body,
        bodySmall = caption,
        labelLarge = username,
        labelMedium = caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        labelSmall = caption,
    )
}

/**
 * LUMINA theme. Dynamic color is intentionally **off**: the palette must match LUMINA web exactly.
 */
@Composable
public fun LuminaTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    LuminaTheme(dark = dark, content = content)
}

@Composable
public fun LuminaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkLuminaColors else LightLuminaColors
    val typography = LuminaTypography()
    CompositionLocalProvider(
        LocalLuminaColors provides colors,
        LocalLuminaTypography provides typography,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = typography.toMaterial(),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(8.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(16.dp),
            ),
            content = content,
        )
    }
}

public object LuminaTheme {
    public val colors: LuminaColors
        @Composable @ReadOnlyComposable get() = LocalLuminaColors.current
    public val typography: LuminaTypography
        @Composable @ReadOnlyComposable get() = LocalLuminaTypography.current
}
