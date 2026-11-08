package rocks.lumina.app.core.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.user.ProfileAvatar

/** "New posts" pill shown when fresh events arrive while scrolled. */
@Composable
public fun NewPostsPill(visible: Boolean, authors: List<String>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible, modifier, enter = fadeIn() + slideInVertically(), exit = fadeOut() + slideOutVertically()) {
        Row(
            Modifier
                .padding(top = 12.dp)
                .clip(LuminaShapes.pill)
                .background(LuminaTheme.colors.accent)
                .tapTarget(label = stringResource(R.string.post_new_posts), onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LuminaIcon(LucideGlyphs.ArrowUp, null, size = 16.dp, tint = LuminaTheme.colors.onPrimary)
            Spacer(Modifier.width(6.dp))
            authors.take(3).forEach { pk ->
                ProfileAvatar(pk, 20.dp)
                Spacer(Modifier.width(2.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.post_new_posts), style = LuminaTheme.typography.username, color = LuminaTheme.colors.onPrimary)
        }
    }
}
