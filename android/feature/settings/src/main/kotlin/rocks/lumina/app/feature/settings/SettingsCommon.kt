package rocks.lumina.app.feature.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

/** Screen frame used by every settings page: top bar + scrolling column inside the 470dp content column. */
@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: (@Composable () -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        LuminaTopBar(title = title, onBack = onBack, actions = actions)
        ContentColumn(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize().then(if (bottomBar == null) Modifier.navigationBarsPadding().imePadding() else Modifier),
                contentPadding = PaddingValues(bottom = 24.dp),
                content = content,
            )
        }
        bottomBar?.invoke()
    }
}

/** Sticky "unsaved changes" bar with Discard/Save. */
@Composable
internal fun UnsavedBar(saving: Boolean, canSave: Boolean, onDiscard: () -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(LuminaTheme.colors.surface)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LuminaDimens.gutter, vertical = 10.dp)
                .navigationBarsPadding()
                .imePadding()
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.settings_unsaved),
                style = LuminaTheme.typography.caption,
                color = LuminaTheme.colors.mutedForeground,
                modifier = Modifier.weight(1f),
            )
            LuminaButton(stringResource(R.string.settings_discard), onDiscard, style = ButtonStyle.Ghost, enabled = !saving)
            LuminaButton(stringResource(R.string.settings_save), onSave, loading = saving, enabled = canSave && !saving)
        }
    }
}

/** Text field + Add button used to append to a list. */
@Composable
internal fun AddField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Uri,
    glyph: LucideGlyph = LucideGlyphs.Plus,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LuminaTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            label = label,
            placeholder = placeholder,
            isError = error != null,
            supportingText = error,
            enabled = enabled,
            leadingGlyph = glyph,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onDone = { onAdd() }),
        )
        LuminaButton(
            stringResource(R.string.settings_add),
            onAdd,
            enabled = enabled && value.isNotBlank(),
            style = ButtonStyle.Secondary,
            modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp),
        )
    }
}

@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = LuminaTheme.typography.caption,
        color = LuminaTheme.colors.mutedForeground,
        modifier = modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
    )
}

/** Shown on account-bound pages when nobody is logged in. */
@Composable
internal fun LoginRequired(onLogin: () -> Unit) {
    EmptyState(
        title = stringResource(R.string.settings_logged_out_title),
        message = stringResource(R.string.settings_login_required),
        glyph = LucideGlyphs.Lock,
        actionLabel = stringResource(R.string.settings_log_in),
        onAction = onLogin,
    )
}

/** Skeleton for list-like settings pages. */
@Composable
internal fun SettingsListSkeleton(rows: Int = 4, avatars: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Skeleton(Modifier.padding(horizontal = LuminaDimens.gutter).size(width = 120.dp, height = 12.dp))
        Spacer(Modifier.height(12.dp))
        repeat(rows) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (avatars) SkeletonCircle(LuminaDimens.avatarList) else Skeleton(Modifier.size(24.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Skeleton(Modifier.fillMaxWidth(0.7f).height(14.dp))
                    Skeleton(Modifier.fillMaxWidth(0.4f).height(10.dp))
                }
            }
        }
    }
}

@Composable
internal fun RemoveButton(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    LuminaIconButton(
        LucideGlyphs.X,
        stringResource(R.string.settings_remove, label),
        onClick,
        enabled = enabled,
        tint = LuminaTheme.colors.mutedForeground,
        iconSize = 20.dp,
    )
}

// ---- platform helpers ------------------------------------------------------------------

internal fun copyText(context: Context, text: String, sensitive: Boolean = false) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText("LUMINA", text)
    if (sensitive) {
        clip.description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
    }
    clipboard.setPrimaryClip(clip)
}

internal fun pasteText(context: Context): String? {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Blocks screenshots while a secret is visible. */
@Composable
internal fun SecureWindowEffect() {
    if (LocalInspectionMode.current) return
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

internal const val PREVIEW_PK = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"
internal const val PREVIEW_PK2 = "82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2"
