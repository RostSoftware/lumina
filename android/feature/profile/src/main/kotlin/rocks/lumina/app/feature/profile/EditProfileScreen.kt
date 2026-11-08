package rocks.lumina.app.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import rocks.lumina.app.core.designsystem.component.Avatar
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.Skeleton
import rocks.lumina.app.core.designsystem.component.SkeletonCircle
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.ui.util.MessageEffect

@Composable
fun EditProfileRoute(onBack: () -> Unit) {
    val vm: EditProfileViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    MessageEffect(vm.messages)
    LaunchedEffect(vm) { vm.saved.collect { onBack() } }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.upload(ProfileField.Picture, uri)
    }
    val pickBanner = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.upload(ProfileField.Banner, uri)
    }
    val imagesOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    EditProfileScreen(
        state = state,
        onBack = onBack,
        onChange = vm::onChange,
        onSave = vm::save,
        onPickAvatar = { pickAvatar.launch(imagesOnly) },
        onPickBanner = { pickBanner.launch(imagesOnly) },
        onLogin = vm::requestLogin,
    )
}

@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    onBack: () -> Unit,
    onChange: (ProfileField, String) -> Unit,
    onSave: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(LuminaTheme.colors.background)) {
        LuminaTopBar(
            title = stringResource(R.string.profile_edit_title),
            onBack = onBack,
            actions = {
                if (!state.loggedOut) {
                    LuminaButton(
                        text = stringResource(R.string.profile_edit_save),
                        onClick = onSave,
                        style = ButtonStyle.Ghost,
                        enabled = state.canSave,
                        loading = state.saving,
                    )
                }
            },
        )
        ContentColumn(Modifier.imePadding()) {
            when {
                state.loggedOut -> EmptyState(
                    title = stringResource(R.string.profile_logged_out_title),
                    message = stringResource(R.string.profile_logged_out_message),
                    glyph = LucideGlyphs.User,
                    actionLabel = stringResource(R.string.profile_log_in),
                    onAction = onLogin,
                )
                state.loading -> EditSkeleton()
                else -> EditForm(state, onChange, onPickAvatar, onPickBanner)
            }
        }
    }
}

@Composable
private fun EditForm(
    state: EditProfileUiState,
    onChange: (ProfileField, String) -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
) {
    val colors = LuminaTheme.colors
    val enabled = !state.saving
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
    ) {
        // Banner preview
        Box(
            Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(colors.surfaceMuted),
        ) {
            UrlSafety.httpsOrNull(state.value(ProfileField.Banner))?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.profile_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Avatar(
                url = UrlSafety.httpsOrNull(state.value(ProfileField.Picture)),
                seed = state.pubkey.orEmpty(),
                contentDescription = stringResource(R.string.profile_edit_avatar_preview),
                size = LuminaDimens.avatarProfile,
                initial = state.value(ProfileField.DisplayName).ifBlank { state.value(ProfileField.Name) },
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UploadButton(stringResource(R.string.profile_edit_change_photo), state.uploads[ProfileField.Picture], enabled, onPickAvatar)
                UploadButton(stringResource(R.string.profile_edit_change_banner), state.uploads[ProfileField.Banner], enabled, onPickBanner)
            }
        }
        Column(Modifier.padding(horizontal = LuminaDimens.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Field(state, ProfileField.DisplayName, R.string.profile_edit_display_name, onChange, enabled)
            Field(state, ProfileField.Name, R.string.profile_edit_name, onChange, enabled)
            Field(state, ProfileField.About, R.string.profile_edit_about, onChange, enabled, singleLine = false)
            Field(state, ProfileField.Website, R.string.profile_edit_website, onChange, enabled, keyboard = KeyboardType.Uri, hint = R.string.profile_edit_website_hint)
            Field(state, ProfileField.Nip05, R.string.profile_edit_nip05, onChange, enabled, keyboard = KeyboardType.Email, hint = R.string.profile_edit_nip05_hint)
            Field(state, ProfileField.Lud16, R.string.profile_edit_lud16, onChange, enabled, keyboard = KeyboardType.Email, hint = R.string.profile_edit_lud16_hint)
            Field(state, ProfileField.Picture, R.string.profile_edit_picture_url, onChange, enabled, keyboard = KeyboardType.Uri)
            Field(state, ProfileField.Banner, R.string.profile_edit_banner_url, onChange, enabled, keyboard = KeyboardType.Uri)
        }
    }
}

@Composable
private fun UploadButton(text: String, progress: Float?, enabled: Boolean, onClick: () -> Unit) {
    Column {
        LuminaButton(
            text = if (progress != null) stringResource(R.string.profile_edit_uploading) else text,
            onClick = onClick,
            style = ButtonStyle.Secondary,
            enabled = enabled && progress == null,
            loading = progress != null,
            leadingIcon = LucideGlyphs.Upload,
            modifier = Modifier.fillMaxWidth(),
        )
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().clip(LuminaShapes.pill),
                color = LuminaTheme.colors.accent,
                trackColor = LuminaTheme.colors.surfaceMuted,
            )
        }
    }
}

@Composable
private fun Field(
    state: EditProfileUiState,
    field: ProfileField,
    label: Int,
    onChange: (ProfileField, String) -> Unit,
    enabled: Boolean,
    singleLine: Boolean = true,
    keyboard: KeyboardType = KeyboardType.Text,
    hint: Int? = null,
) {
    val error = state.errors[field]
    LuminaTextField(
        value = state.value(field),
        onValueChange = { onChange(field, it) },
        label = stringResource(label),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        enabled = enabled,
        isError = error != null,
        supportingText = when (error) {
            FieldError.NotHttps -> stringResource(R.string.profile_edit_error_https)
            FieldError.InvalidAddress -> stringResource(R.string.profile_edit_error_address)
            null -> hint?.let { stringResource(it) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EditSkeleton() {
    Column(Modifier.fillMaxWidth()) {
        Skeleton(Modifier.fillMaxWidth().height(110.dp), shape = androidx.compose.ui.graphics.RectangleShape)
        Row(Modifier.padding(LuminaDimens.gutter), verticalAlignment = Alignment.CenterVertically) {
            SkeletonCircle(LuminaDimens.avatarProfile)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.fillMaxWidth().height(44.dp))
                Skeleton(Modifier.fillMaxWidth().height(44.dp))
            }
        }
        Column(Modifier.padding(horizontal = LuminaDimens.gutter), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { Skeleton(Modifier.fillMaxWidth().height(56.dp)) }
        }
    }
}

// ---- previews --------------------------------------------------------------------------------

private val previewEdit = run {
    val values = mapOf(
        ProfileField.Name to "alice",
        ProfileField.DisplayName to "Alice",
        ProfileField.About to "Photographer. Mountains, film and #nostr.",
        ProfileField.Website to "http://alice.example",
        ProfileField.Lud16 to "alice@getalby.com",
    )
    EditProfileUiState(
        loading = false, pubkey = previewPubkey(1), original = values, fields = values,
        errors = mapOf(ProfileField.Website to FieldError.NotHttps), uploads = mapOf(ProfileField.Picture to 0.4f),
    )
}

@Preview(name = "Edit · light", showBackground = true)
@Composable
private fun EditProfileLightPreview() {
    LuminaTheme(dark = false) { EditProfileScreen(previewEdit, {}, { _, _ -> }, {}, {}, {}, {}) }
}

@Preview(name = "Edit · dark", showBackground = true)
@Composable
private fun EditProfileDarkPreview() {
    LuminaTheme(dark = true) { EditProfileScreen(previewEdit.copy(uploads = emptyMap(), saving = true), {}, { _, _ -> }, {}, {}, {}, {}) }
}

@Preview(name = "Edit loading · light", showBackground = true)
@Composable
private fun EditProfileLoadingPreview() {
    LuminaTheme(dark = false) { EditProfileScreen(EditProfileUiState(), {}, { _, _ -> }, {}, {}, {}, {}) }
}
