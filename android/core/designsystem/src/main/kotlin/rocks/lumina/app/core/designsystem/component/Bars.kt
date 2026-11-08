package rocks.lumina.app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.R
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/**
 * Top bar: 56 high, `surface`, hairline bottom border. Includes the status bar inset.
 * Pass [onBack] for the back arrow + title layout; [leading] replaces it (e.g. the Home wordmark).
 */
@Composable
public fun LuminaTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable RowScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(LuminaTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .contentColumn()
                    .height(LuminaDimens.barHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading()
                } else {
                    if (onBack != null) {
                        LuminaIconButton(
                            glyph = LucideGlyphs.ArrowLeft,
                            contentDescription = stringResource(R.string.ds_back),
                            onClick = onBack,
                        )
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    if (title != null) {
                        Text(
                            text = title,
                            style = LuminaTheme.typography.title,
                            color = LuminaTheme.colors.foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .semantics { heading() },
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
        Hairline()
    }
}

/** One destination in the bottom bar. */
public class BottomBarItem(
    public val key: String,
    public val contentDescription: String,
    public val icon: @Composable (active: Boolean) -> Unit,
    public val showBadge: Boolean = false,
)

/**
 * Bottom navigation bar used on every window size (no rail, no sidebar). 56 + bottom inset, `surface`,
 * hairline top border. Background spans the width; icons sit inside the centered content column.
 */
@Composable
public fun LuminaBottomBar(
    items: List<BottomBarItem>,
    selectedKey: String?,
    onSelect: (BottomBarItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(LuminaTheme.colors.surface)
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Hairline()
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Row(
                Modifier
                    .contentColumn()
                    .height(LuminaDimens.barHeight),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEach { item ->
                    val active = item.key == selectedKey
                    Box(
                        Modifier
                            .weight(1f)
                            .height(LuminaDimens.barHeight)
                            .tapTarget(
                                role = androidx.compose.ui.semantics.Role.Tab,
                                label = item.contentDescription,
                                selected = active,
                                onClick = { onSelect(item) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box {
                            item.icon(active)
                            if (item.showBadge) {
                                UnreadDot(Modifier.align(Alignment.TopEnd).padding(top = 0.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small `like`-red unread dot. */
@Composable
public fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(0.dp)
            .background(LuminaTheme.colors.like, shape = androidx.compose.foundation.shape.CircleShape)
            .width(8.dp)
            .height(8.dp),
    )
}

/** The LUMINA wordmark, drawn with the brand gradient. */
@Composable
public fun Wordmark(modifier: Modifier = Modifier) {
    val brush = rocks.lumina.app.core.designsystem.theme.brandGradient(300f)
    Text(
        text = stringResource(R.string.ds_wordmark),
        style = LuminaTheme.typography.wordmark.copy(brush = brush),
        modifier = modifier.semantics { heading() },
    )
}
