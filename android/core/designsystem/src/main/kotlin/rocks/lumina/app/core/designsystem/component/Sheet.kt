package rocks.lumina.app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/** Bottom sheet (16 top radius, `surface`) used for post actions, zap amounts, share, etc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun LuminaBottomSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        shape = LuminaShapes.sheet,
        containerColor = LuminaTheme.colors.surface,
        contentColor = LuminaTheme.colors.foreground,
        scrimColor = Color.Black.copy(alpha = 0.4f),
        sheetMaxWidth = LuminaDimens.contentMaxWidth,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = LuminaDimens.contentMaxWidth)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        ) {
            if (title != null) {
                Text(
                    title,
                    style = LuminaTheme.typography.title,
                    color = LuminaTheme.colors.foreground,
                    modifier = Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                )
            }
            content()
        }
    }
}

/** A row inside a [LuminaBottomSheet]. */
@Composable
public fun SheetAction(
    text: String,
    glyph: LucideGlyph?,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    SettingsRow(
        title = text,
        glyph = glyph,
        titleColor = if (destructive) LuminaTheme.colors.destructive else LuminaTheme.colors.foreground,
        onClick = onClick,
        trailing = {},
    )
}
