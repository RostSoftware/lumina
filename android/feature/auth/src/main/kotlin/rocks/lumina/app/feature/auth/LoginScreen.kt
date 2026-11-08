package rocks.lumina.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.selection.SelectionContainer
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.Wordmark
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyph
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.ui.sheet.QrCode
import rocks.lumina.app.core.ui.user.ProfileAvatar

/** Everything the login screen can do. Grouped so the stateless screen stays readable. */
class LoginActions(
    val close: () -> Unit = {},
    val back: () -> Unit = {},
    val open: (LoginStep) -> Unit = {},
    val amberLogin: (packageName: String) -> Unit = {},
    val getAmber: () -> Unit = {},
    val setBunker: (String) -> Unit = {},
    val pasteBunker: () -> Unit = {},
    val scanBunker: () -> Unit = {},
    val connectBunker: () -> Unit = {},
    val cancelBunker: () -> Unit = {},
    val openNostrConnect: (String) -> Unit = {},
    val copyNostrConnect: (String) -> Unit = {},
    val retryNostrConnect: () -> Unit = {},
    val setNsec: (String) -> Unit = {},
    val loginNsec: () -> Unit = {},
    val createAccount: () -> Unit = {},
    val setDisplayName: (String) -> Unit = {},
    val setAbout: (String) -> Unit = {},
    val saveProfile: () -> Unit = {},
    val skipProfile: () -> Unit = {},
    val revealBackup: () -> Unit = {},
    val copyBackup: (String) -> Unit = {},
    val finishBackup: () -> Unit = {},
)

@Composable
fun LoginScreen(state: LoginUiState, actions: LoginActions, modifier: Modifier = Modifier) {
    val title = when (state.step) {
        LoginStep.Choose, LoginStep.Creating -> stringResource(R.string.auth_title)
        LoginStep.Bunker -> stringResource(R.string.auth_bunker_title)
        LoginStep.NostrConnect -> stringResource(R.string.auth_nc_title)
        LoginStep.Nsec -> stringResource(R.string.auth_nsec_title)
        LoginStep.Profile -> stringResource(R.string.auth_profile_title)
        LoginStep.Backup -> stringResource(R.string.auth_backup_title)
    }
    Column(modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        when (state.step) {
            LoginStep.Choose -> LuminaTopBar(
                title = title,
                leading = {
                    LuminaIconButton(LucideGlyphs.X, stringResource(R.string.auth_close), actions.close)
                    ScreenTitle(title)
                },
            )
            LoginStep.Bunker, LoginStep.NostrConnect, LoginStep.Nsec -> LuminaTopBar(title = title, onBack = actions.back)
            LoginStep.Creating, LoginStep.Profile, LoginStep.Backup -> LuminaTopBar(title = title)
        }
        ContentColumn(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = LuminaDimens.gutter, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.step) {
                    LoginStep.Choose -> ChooseStep(state, actions)
                    LoginStep.Bunker -> BunkerStep(state, actions)
                    LoginStep.NostrConnect -> NostrConnectStep(state, actions)
                    LoginStep.Nsec -> NsecStep(state, actions)
                    LoginStep.Creating -> Waiting(stringResource(R.string.auth_creating), null, null)
                    LoginStep.Profile -> ProfileStep(state, actions)
                    LoginStep.Backup -> BackupStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(text: String) {
    Text(
        text,
        style = LuminaTheme.typography.title,
        color = LuminaTheme.colors.foreground,
        modifier = Modifier.padding(start = 4.dp).semantics { heading() },
    )
}

// ---- chooser ---------------------------------------------------------------------------

@Composable
private fun ColumnScope.ChooseStep(state: LoginUiState, actions: LoginActions) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Wordmark()
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.auth_tagline),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.mutedForeground,
            textAlign = TextAlign.Center,
        )
    }

    if (state.signerInstalled) {
        Text(
            stringResource(R.string.auth_recommended).uppercase(),
            style = LuminaTheme.typography.caption,
            color = LuminaTheme.colors.accent,
        )
        val single = state.signerApps.size == 1
        state.signerApps.forEach { app ->
            LuminaButton(
                text = if (single && app.label.contains("Amber", ignoreCase = true)) {
                    stringResource(R.string.auth_amber)
                } else {
                    stringResource(R.string.auth_amber_named, app.label)
                },
                onClick = { actions.amberLogin(app.packageName) },
                loading = state.amberBusy,
                enabled = !state.amberBusy,
                leadingIcon = LucideGlyphs.Smartphone,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Text(stringResource(R.string.auth_amber_desc), style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
    } else {
        MethodCard(
            glyph = LucideGlyphs.Smartphone,
            title = stringResource(R.string.auth_get_amber),
            description = stringResource(R.string.auth_get_amber_desc),
            trailingGlyph = LucideGlyphs.ExternalLink,
            onClick = actions.getAmber,
        )
    }

    Spacer(Modifier.height(4.dp))
    MethodCard(LucideGlyphs.Link, stringResource(R.string.auth_bunker), stringResource(R.string.auth_bunker_desc)) {
        actions.open(LoginStep.Bunker)
    }
    MethodCard(LucideGlyphs.QrCode, stringResource(R.string.auth_nostrconnect), stringResource(R.string.auth_nostrconnect_desc)) {
        actions.open(LoginStep.NostrConnect)
    }

    SectionHeader(stringResource(R.string.auth_other_options), Modifier.padding(horizontal = 0.dp))
    MethodCard(LucideGlyphs.KeyRound, stringResource(R.string.auth_nsec), stringResource(R.string.auth_nsec_desc)) {
        actions.open(LoginStep.Nsec)
    }
    MethodCard(LucideGlyphs.UserPlus, stringResource(R.string.auth_create), stringResource(R.string.auth_create_desc), onClick = actions.createAccount)
}

@Composable
private fun MethodCard(
    glyph: LucideGlyph,
    title: String,
    description: String,
    trailingGlyph: LucideGlyph = LucideGlyphs.ChevronRight,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, LuminaTheme.colors.border, LuminaShapes.control)
            .tapTarget(label = title, onClick = onClick)
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LuminaIcon(glyph, null)
        Column(Modifier.weight(1f)) {
            Text(title, style = LuminaTheme.typography.username, color = LuminaTheme.colors.foreground)
            Text(description, style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
        }
        LuminaIcon(trailingGlyph, null, tint = LuminaTheme.colors.mutedForeground, size = 18.dp)
    }
}

// ---- bunker ----------------------------------------------------------------------------

@Composable
private fun BunkerStep(state: LoginUiState, actions: LoginActions) {
    if (state.bunkerWaiting) {
        Waiting(
            title = stringResource(R.string.auth_waiting_title),
            body = stringResource(R.string.auth_waiting_body),
            onCancel = actions.cancelBunker,
        )
        return
    }
    Text(stringResource(R.string.auth_bunker_desc), style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground)
    LuminaTextField(
        value = state.bunkerInput,
        onValueChange = actions.setBunker,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.auth_bunker_label),
        placeholder = stringResource(R.string.auth_bunker_placeholder),
        singleLine = false,
        maxLines = 4,
        isError = state.bunkerInvalid,
        supportingText = if (state.bunkerInvalid) stringResource(R.string.auth_bunker_invalid) else null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { actions.connectBunker() }),
        leadingGlyph = LucideGlyphs.Link,
        trailing = { LuminaIconButton(LucideGlyphs.ClipboardPaste, stringResource(R.string.auth_paste), actions.pasteBunker) },
    )
    LuminaButton(
        text = stringResource(R.string.auth_connect),
        onClick = actions.connectBunker,
        enabled = state.bunkerInput.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    )
    LuminaButton(
        text = stringResource(R.string.auth_scan),
        onClick = actions.scanBunker,
        style = ButtonStyle.Outline,
        leadingIcon = LucideGlyphs.ScanLine,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- nostrconnect ----------------------------------------------------------------------

@Composable
private fun NostrConnectStep(state: LoginUiState, actions: LoginActions) {
    val uri = state.nostrConnectUri ?: return
    Text(
        stringResource(R.string.auth_nc_body),
        style = LuminaTheme.typography.body,
        color = LuminaTheme.colors.mutedForeground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        QrCode(uri, size = 240.dp)
    }
    if (state.nostrConnectFailed) {
        Text(
            stringResource(R.string.auth_nc_failed),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.destructive,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        )
        LuminaButton(stringResource(R.string.auth_retry), actions.retryNostrConnect, leadingIcon = LucideGlyphs.RefreshCw, modifier = Modifier.fillMaxWidth())
    } else {
        Row(
            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spinner(size = 16.dp)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.auth_nc_waiting), style = LuminaTheme.typography.caption, color = LuminaTheme.colors.mutedForeground)
        }
        LuminaButton(
            stringResource(R.string.auth_nc_open),
            { actions.openNostrConnect(uri) },
            leadingIcon = LucideGlyphs.ExternalLink,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    LuminaButton(
        stringResource(R.string.auth_nc_copy),
        { actions.copyNostrConnect(uri) },
        style = ButtonStyle.Outline,
        leadingIcon = LucideGlyphs.Copy,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- nsec ------------------------------------------------------------------------------

@Composable
private fun NsecStep(state: LoginUiState, actions: LoginActions) {
    SecureWindowEffect()
    var visible by remember { mutableStateOf(false) }
    Notice(LucideGlyphs.TriangleAlert, stringResource(R.string.auth_nsec_warning), LuminaTheme.colors.zap)
    LuminaTextField(
        value = state.nsecInput,
        onValueChange = actions.setNsec,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.auth_nsec_label),
        isError = state.nsecInvalid,
        supportingText = if (state.nsecInvalid) stringResource(R.string.auth_nsec_invalid) else null,
        enabled = !state.nsecBusy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { actions.loginNsec() }),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        leadingGlyph = LucideGlyphs.KeyRound,
        trailing = {
            LuminaIconButton(
                if (visible) LucideGlyphs.EyeOff else LucideGlyphs.Eye,
                stringResource(if (visible) R.string.auth_hide_key else R.string.auth_show_key),
                { visible = !visible },
            )
        },
    )
    LuminaButton(
        stringResource(R.string.auth_login),
        actions.loginNsec,
        enabled = state.nsecInput.isNotBlank() && !state.nsecBusy,
        loading = state.nsecBusy,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- new account -----------------------------------------------------------------------

@Composable
private fun ProfileStep(state: LoginUiState, actions: LoginActions) {
    Text(stringResource(R.string.auth_profile_body), style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground)
    state.newPubkey?.let { pk ->
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            ProfileAvatar(pk, LuminaDimens.avatarProfile)
        }
    }
    LuminaTextField(
        value = state.displayName,
        onValueChange = actions.setDisplayName,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.auth_profile_name),
        enabled = !state.profileSaving,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    LuminaTextField(
        value = state.about,
        onValueChange = actions.setAbout,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(R.string.auth_profile_about),
        singleLine = false,
        minLines = 3,
        enabled = !state.profileSaving,
    )
    LuminaButton(
        stringResource(R.string.auth_profile_save),
        actions.saveProfile,
        loading = state.profileSaving,
        enabled = !state.profileSaving,
        modifier = Modifier.fillMaxWidth(),
    )
    LuminaButton(
        stringResource(R.string.auth_skip),
        actions.skipProfile,
        style = ButtonStyle.Ghost,
        enabled = !state.profileSaving,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BackupStep(state: LoginUiState, actions: LoginActions) {
    SecureWindowEffect()
    val nsec = state.backupNsec
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        LuminaIcon(LucideGlyphs.KeyRound, null, size = 40.dp)
    }
    Notice(LucideGlyphs.TriangleAlert, stringResource(R.string.auth_backup_body), LuminaTheme.colors.destructive)
    Text(stringResource(R.string.auth_backup_tip), style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground)
    if (nsec == null) {
        Text(stringResource(R.string.auth_backup_unavailable), style = LuminaTheme.typography.body, color = LuminaTheme.colors.destructive)
        LuminaButton(stringResource(R.string.auth_profile_save), actions.finishBackup, modifier = Modifier.fillMaxWidth())
        return
    }
    SecretKeyBox(nsec, state.backupRevealed, actions.revealBackup)
    LuminaButton(
        stringResource(R.string.auth_backup_copy),
        { actions.copyBackup(nsec) },
        style = ButtonStyle.Outline,
        leadingIcon = if (state.backupCopied) LucideGlyphs.Check else LucideGlyphs.Copy,
        modifier = Modifier.fillMaxWidth(),
    )
    LuminaButton(
        stringResource(R.string.auth_backup_done),
        actions.finishBackup,
        enabled = state.backupRevealed || state.backupCopied,
        modifier = Modifier.fillMaxWidth(),
    )
    LuminaButton(stringResource(R.string.auth_backup_later), actions.finishBackup, style = ButtonStyle.Ghost, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun SecretKeyBox(nsec: String, revealed: Boolean, onReveal: () -> Unit) {
    val colors = LuminaTheme.colors
    val base = Modifier
        .fillMaxWidth()
        .background(colors.surfaceMuted, LuminaShapes.control)
        .heightIn(min = 72.dp)
    if (revealed) {
        SelectionContainer {
            Text(
                nsec,
                style = LuminaTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                color = colors.foreground,
                modifier = base.padding(14.dp),
            )
        }
    } else {
        Row(
            base.tapTarget(label = stringResource(R.string.auth_backup_reveal), onClick = onReveal).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            LuminaIcon(LucideGlyphs.EyeOff, null, tint = colors.mutedForeground, size = 18.dp)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.auth_backup_reveal), style = LuminaTheme.typography.username, color = colors.foreground)
        }
    }
}

// ---- shared bits -----------------------------------------------------------------------

@Composable
private fun Notice(glyph: LucideGlyph, text: String, tint: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, tint, LuminaShapes.control)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LuminaIcon(glyph, null, tint = tint, size = 20.dp)
        Text(text, style = LuminaTheme.typography.body, color = LuminaTheme.colors.foreground, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Waiting(title: String, body: String?, onCancel: (() -> Unit)?) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spinner(size = 32.dp)
        Text(title, style = LuminaTheme.typography.title, color = LuminaTheme.colors.foreground, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = LuminaTheme.typography.body, color = LuminaTheme.colors.mutedForeground, textAlign = TextAlign.Center)
        }
        if (onCancel != null) {
            LuminaButton(stringResource(R.string.auth_cancel), onCancel, style = ButtonStyle.Outline)
        }
    }
}

// ---- previews --------------------------------------------------------------------------

private const val PREVIEW_PK = "3bf0c63fcb93463407af97a5e5ee64fa883d107ef9e558472c4eb9aaaefa459d"

@Preview(name = "Choose – light")
@Composable
private fun ChooseLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(signerApps = listOf(SignerApp("com.greenart7c3.nostrsigner", "Amber"))), LoginActions())
}

@Preview(name = "Choose – dark, no signer")
@Composable
private fun ChooseDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(LoginUiState(), LoginActions())
}

@Preview(name = "Bunker – light")
@Composable
private fun BunkerLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(step = LoginStep.Bunker, bunkerInput = "bunker://abc", bunkerInvalid = true), LoginActions())
}

@Preview(name = "Bunker waiting – dark")
@Composable
private fun BunkerDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(LoginUiState(step = LoginStep.Bunker, bunkerWaiting = true), LoginActions())
}

@Preview(name = "Nostrconnect – light")
@Composable
private fun NostrConnectLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(step = LoginStep.NostrConnect, nostrConnectUri = "nostrconnect://$PREVIEW_PK?relay=wss://relay.ditto.pub"), LoginActions())
}

@Preview(name = "Nostrconnect failed – dark")
@Composable
private fun NostrConnectDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(
        LoginUiState(step = LoginStep.NostrConnect, nostrConnectUri = "nostrconnect://$PREVIEW_PK?relay=wss://relay.ditto.pub", nostrConnectFailed = true),
        LoginActions(),
    )
}

@Preview(name = "Nsec – light")
@Composable
private fun NsecLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(step = LoginStep.Nsec, nsecInput = "nsec1"), LoginActions())
}

@Preview(name = "Nsec – dark")
@Composable
private fun NsecDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(LoginUiState(step = LoginStep.Nsec, nsecBusy = true, nsecInput = "nsec1xyz"), LoginActions())
}

@Preview(name = "Profile – light")
@Composable
private fun ProfileLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(step = LoginStep.Profile, newPubkey = PREVIEW_PK, displayName = "Alice"), LoginActions())
}

@Preview(name = "Profile – dark")
@Composable
private fun ProfileDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(LoginUiState(step = LoginStep.Profile, newPubkey = PREVIEW_PK, profileSaving = true), LoginActions())
}

@Preview(name = "Backup – light")
@Composable
private fun BackupLightPreview() = LuminaTheme(dark = false) {
    LoginScreen(LoginUiState(step = LoginStep.Backup, backupNsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5"), LoginActions())
}

@Preview(name = "Backup revealed – dark")
@Composable
private fun BackupDarkPreview() = LuminaTheme(dark = true) {
    LoginScreen(
        LoginUiState(step = LoginStep.Backup, backupNsec = "nsec1vl029mgpspedva04g90vltkh6fvh240zqtv9k0t9af8935ke9laqsnlfe5", backupRevealed = true),
        LoginActions(),
    )
}
