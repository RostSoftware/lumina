package rocks.lumina.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/**
 * The single centered content column (max 470 wide) used on every screen size. On wide screens the
 * area outside the column is plain `background`.
 */
@Composable
public fun ContentColumn(
    modifier: Modifier = Modifier,
    fillHeight: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
            .background(LuminaTheme.colors.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = LuminaDimens.contentMaxWidth)
                .fillMaxWidth()
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier),
            content = content,
        )
    }
}

/** Modifier that constrains content to the centered column. */
public fun Modifier.contentColumn(): Modifier = this.widthIn(max = LuminaDimens.contentMaxWidth).fillMaxWidth()

/** Hairline divider using the `border` token. */
@Composable
public fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = LuminaDimens.hairline, color = LuminaTheme.colors.border)
}

@Composable
public fun ScreenBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(LuminaTheme.colors.background), content = content)
}

@Composable
public fun VerticalSpace(height: androidx.compose.ui.unit.Dp) {
    Box(Modifier.height(height))
}
