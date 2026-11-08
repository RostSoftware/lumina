package rocks.lumina.app.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import rocks.lumina.app.core.designsystem.R

private fun interFont(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Inter, bundled as a variable font. */
public val Inter: FontFamily = FontFamily(
    interFont(400),
    interFont(500),
    interFont(600),
    interFont(700),
)

/** Type scale from the root AGENTS.md. Sizes use `sp`, so system font scaling is respected. */
@Immutable
public data class LuminaTypography(
    val wordmark: TextStyle = TextStyle(fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
    val title: TextStyle = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp),
    val body: TextStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    val username: TextStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val caption: TextStyle = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    val count: TextStyle = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp),
    val headline: TextStyle = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 26.sp),
)
