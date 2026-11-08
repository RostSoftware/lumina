package rocks.lumina.app.feature.create

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ContentColumn
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LoadingBox
import rocks.lumina.app.core.designsystem.component.LuminaBottomSheet
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaSwitch
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.LuminaTopBar
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.SheetAction
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.component.tapTarget
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.media.CropRect
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.ui.user.ProfileAvatar
import rocks.lumina.app.core.ui.user.UserName
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.util.resolve

/** Every user action of the create flow. */
class CreateActions(
    val onClose: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onPickGallery: () -> Unit = {},
    val onTakePhoto: () -> Unit = {},
    val onSelect: (Int) -> Unit = {},
    val onMove: (index: Int, delta: Int) -> Unit = { _, _ -> },
    val onRemove: (String) -> Unit = {},
    val onCrop: (String) -> Unit = {},
    val onAltChange: (String, String) -> Unit = { _, _ -> },
    val onNext: () -> Unit = {},
    val onBackToEdit: () -> Unit = {},
    val onCaptionChange: (String) -> Unit = {},
    val onAddHashtag: (String) -> Boolean = { true },
    val onRemoveHashtag: (String) -> Unit = {},
    val onPersonQueryChange: (String) -> Unit = {},
    val onAddPerson: (String) -> Unit = {},
    val onRemovePerson: (String) -> Unit = {},
    val onLocationChange: (String) -> Unit = {},
    val onContentWarningChange: (Boolean) -> Unit = {},
    val onContentWarningReasonChange: (String) -> Unit = {},
    val onRetryUpload: (String) -> Unit = {},
    val onPublish: () -> Unit = {},
    val onRetryPublish: () -> Unit = {},
)

@Composable
fun CreateScreen(state: CreateUiState, actions: CreateActions, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(LuminaTheme.colors.background),
    ) {
        when {
            !state.ready -> Column(Modifier.fillMaxSize()) {
                CloseTopBar(onClose = actions.onClose)
                LoadingBox(Modifier.weight(1f))
            }
            !state.loggedIn -> LoggedOut(actions)
            state.step == CreateStep.Pick || state.images.isEmpty() -> PickStep(actions)
            state.step == CreateStep.Edit -> EditStep(state, actions)
            else -> DetailsStep(state, actions)
        }
    }
}

@Composable
private fun CloseTopBar(onClose: () -> Unit, actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {}) {
    LuminaTopBar(
        leading = {
            LuminaIconButton(LucideGlyphs.X, stringResource(R.string.create_close), onClose)
            Text(
                stringResource(R.string.create_title),
                style = LuminaTheme.typography.title,
                color = LuminaTheme.colors.foreground,
                modifier = Modifier.padding(start = 4.dp),
            )
        },
        actions = actions,
    )
}

@Composable
private fun LoggedOut(actions: CreateActions) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        CloseTopBar(onClose = actions.onClose)
        ContentColumn(Modifier.weight(1f)) {
            EmptyState(
                title = stringResource(R.string.create_login_title),
                message = stringResource(R.string.create_login_message),
                glyph = LucideGlyphs.Lock,
                actionLabel = stringResource(R.string.create_login_action),
                onAction = actions.onLogin,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun PickStep(actions: CreateActions) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        CloseTopBar(onClose = actions.onClose)
        ContentColumn(Modifier.weight(1f)) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(LuminaTheme.colors.surfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    LuminaIcon(LucideGlyphs.ImagePlus, null, size = 40.dp)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.create_pick_title), style = LuminaTheme.typography.headline, color = LuminaTheme.colors.foreground)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.create_pick_message, CreateUiState.MAX_IMAGES),
                    style = LuminaTheme.typography.body,
                    color = LuminaTheme.colors.mutedForeground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                LuminaButton(
                    text = stringResource(R.string.create_pick_gallery),
                    onClick = actions.onPickGallery,
                    leadingIcon = LucideGlyphs.Image,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LuminaButton(
                    text = stringResource(R.string.create_pick_camera),
                    onClick = actions.onTakePhoto,
                    style = ButtonStyle.Outline,
                    leadingIcon = LucideGlyphs.Camera,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---- Edit -------------------------------------------------------------------------------------------------------

@Composable
private fun EditStep(state: CreateUiState, actions: CreateActions) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    val images = state.images
    val current = images.getOrNull(state.selected) ?: images.first()
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        CloseTopBar(onClose = actions.onClose) {
            LuminaButton(text = stringResource(R.string.create_next), onClick = actions.onNext, style = ButtonStyle.Ghost)
        }
        ContentColumn(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                val pager = rememberPagerState(initialPage = state.selected) { images.size }
                LaunchedEffect(state.selected, images.size) {
                    if (state.selected in images.indices && pager.currentPage != state.selected) pager.animateScrollToPage(state.selected)
                }
                LaunchedEffect(pager) {
                    snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { actions.onSelect(it) }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(LuminaTheme.colors.surfaceMuted),
                ) {
                    HorizontalPager(state = pager, key = { images.getOrNull(it)?.id ?: it }, modifier = Modifier.fillMaxSize()) { page ->
                        val image = images[page]
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CroppedImage(
                                model = image.uri,
                                imageAspect = image.imageAspect,
                                crop = image.crop,
                                contentDescription = image.alt.ifBlank { stringResource(R.string.create_photo_desc, page + 1) },
                                modifier = Modifier
                                    .tapTarget(label = stringResource(R.string.create_crop)) { actions.onCrop(image.id) }
                                    .aspectRatio(image.frameAspect, matchHeightConstraintsFirst = image.frameAspect < 1f),
                            )
                        }
                    }
                    if (images.size > 1) {
                        Text(
                            stringResource(R.string.create_photo_n, state.selected + 1, images.size),
                            style = LuminaTheme.typography.caption,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clip(LuminaShapes.pill)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                // Per-photo actions.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LuminaButton(
                        text = stringResource(R.string.create_crop),
                        onClick = { actions.onCrop(current.id) },
                        style = ButtonStyle.Ghost,
                        leadingIcon = LucideGlyphs.Crop,
                    )
                    Spacer(Modifier.weight(1f))
                    LuminaIconButton(
                        LucideGlyphs.ChevronLeft,
                        stringResource(R.string.create_move_earlier),
                        onClick = { actions.onMove(state.selected, -1) },
                        enabled = state.selected > 0,
                    )
                    LuminaIconButton(
                        LucideGlyphs.ChevronRight,
                        stringResource(R.string.create_move_later),
                        onClick = { actions.onMove(state.selected, 1) },
                        enabled = state.selected < images.size - 1,
                    )
                    LuminaIconButton(
                        LucideGlyphs.Trash2,
                        stringResource(R.string.create_remove),
                        onClick = { actions.onRemove(current.id) },
                        tint = LuminaTheme.colors.destructive,
                    )
                }
                // Thumbnail strip.
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = LuminaDimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(images, key = { _, it -> it.id }) { index, image ->
                        Thumbnail(
                            image = image,
                            selected = index == state.selected,
                            label = stringResource(R.string.create_thumb_desc, index + 1),
                            onClick = { actions.onSelect(index) },
                        )
                    }
                    if (state.canAddMore) {
                        item(key = "add") {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(LuminaShapes.control)
                                    .background(LuminaTheme.colors.surfaceMuted)
                                    .tapTarget(label = stringResource(R.string.create_add_more)) { showAddSheet = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                LuminaIcon(LucideGlyphs.Plus, null)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                LuminaTextField(
                    value = current.alt,
                    onValueChange = { actions.onAltChange(current.id, it) },
                    label = stringResource(R.string.create_alt_label),
                    placeholder = stringResource(R.string.create_alt_placeholder),
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LuminaDimens.gutter),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showAddSheet) {
        LuminaBottomSheet(onDismiss = { showAddSheet = false }, title = stringResource(R.string.create_add_more)) {
            SheetAction(stringResource(R.string.create_pick_gallery), LucideGlyphs.Image, onClick = {
                showAddSheet = false
                actions.onPickGallery()
            })
            SheetAction(stringResource(R.string.create_pick_camera), LucideGlyphs.Camera, onClick = {
                showAddSheet = false
                actions.onTakePhoto()
            })
        }
    }
}

@Composable
private fun Thumbnail(image: DraftImage, selected: Boolean, label: String, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp = 64.dp) {
    Box(
        Modifier
            .size(size)
            .clip(LuminaShapes.control)
            .then(if (selected) Modifier.border(2.dp, LuminaTheme.colors.accent, LuminaShapes.control) else Modifier)
            .tapTarget(role = Role.Tab, label = label, selected = selected, onClick = onClick)
            .background(LuminaTheme.colors.surfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        CroppedImage(
            model = image.uri,
            imageAspect = image.imageAspect,
            crop = image.thumbCrop,
            contentDescription = null,
            maxDecodeSize = 256,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// ---- Details ----------------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(state: CreateUiState, actions: CreateActions) {
    val colors = LuminaTheme.colors
    val inputsEnabled = !state.busy
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        LuminaTopBar(
            title = stringResource(R.string.create_title),
            onBack = actions.onBackToEdit,
            actions = {
                LuminaButton(
                    text = stringResource(R.string.create_share),
                    onClick = actions.onPublish,
                    enabled = state.canPublish,
                    loading = state.busy,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )
        ContentColumn(Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                PublishStatus(state, actions)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(LuminaDimens.gutter),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.images.firstOrNull()?.let { first ->
                        Box(Modifier.size(72.dp).clip(LuminaShapes.control)) {
                            CroppedImage(
                                model = first.uri,
                                imageAspect = first.imageAspect,
                                crop = first.thumbCrop,
                                contentDescription = null,
                                maxDecodeSize = 256,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    LuminaTextField(
                        value = state.caption,
                        onValueChange = actions.onCaptionChange,
                        placeholder = stringResource(R.string.create_caption_placeholder),
                        label = stringResource(R.string.create_caption_label),
                        singleLine = false,
                        minLines = 3,
                        maxLines = 10,
                        enabled = inputsEnabled,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.weight(1f),
                    )
                }
                Hairline()

                // Uploads
                SectionHeader(stringResource(R.string.create_uploads))
                state.images.forEachIndexed { index, image -> UploadRow(index, image, actions) }
                if (!state.allUploaded) {
                    Text(
                        stringResource(R.string.create_uploads_pending),
                        style = LuminaTheme.typography.caption,
                        color = colors.mutedForeground,
                        modifier = Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                    )
                }

                // Hashtags
                SectionHeader(stringResource(R.string.create_hashtags))
                val hashtags = state.hashtags
                if (hashtags.isNotEmpty()) {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val fromCaption = state.captionHashtags.toSet()
                        hashtags.forEach { tag ->
                            RemovableChip(
                                text = "#$tag",
                                removeLabel = stringResource(R.string.create_hashtag_remove, tag),
                                onRemove = if (tag in fromCaption || !inputsEnabled) null else ({ actions.onRemoveHashtag(tag) }),
                            )
                        }
                    }
                }
                var hashtagInput by rememberSaveable { mutableStateOf("") }
                var hashtagError by rememberSaveable { mutableStateOf(false) }
                val submitHashtag = {
                    if (hashtagInput.isNotBlank()) {
                        val ok = actions.onAddHashtag(hashtagInput)
                        hashtagError = !ok
                        if (ok) hashtagInput = ""
                    }
                }
                LuminaTextField(
                    value = hashtagInput,
                    onValueChange = {
                        hashtagInput = it
                        hashtagError = false
                    },
                    placeholder = stringResource(R.string.create_hashtag_placeholder),
                    leadingGlyph = LucideGlyphs.Hash,
                    enabled = inputsEnabled,
                    isError = hashtagError,
                    supportingText = if (hashtagError) stringResource(R.string.create_hashtag_invalid) else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None),
                    keyboardActions = KeyboardActions(onDone = { submitHashtag() }),
                    trailing = {
                        LuminaIconButton(LucideGlyphs.Plus, stringResource(R.string.create_hashtag_add), onClick = { submitHashtag() }, enabled = hashtagInput.isNotBlank())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                )

                // People
                SectionHeader(stringResource(R.string.create_people))
                if (state.people.isNotEmpty()) {
                    FlowRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.people.forEach { pk -> PersonChip(pk, onRemove = if (inputsEnabled) ({ actions.onRemovePerson(pk) }) else null) }
                    }
                }
                LuminaTextField(
                    value = state.personQuery,
                    onValueChange = actions.onPersonQueryChange,
                    placeholder = stringResource(R.string.create_people_placeholder),
                    leadingGlyph = LucideGlyphs.AtSign,
                    enabled = inputsEnabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.None),
                    trailing = if (state.searchingPeople) ({ Spinner(size = 18.dp) }) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                )
                val results = state.personResults.filterNot { it.pubkey in state.people }
                if (state.personQuery.isNotBlank() && !state.searchingPeople && results.isEmpty()) {
                    Text(
                        stringResource(R.string.create_people_none),
                        style = LuminaTheme.typography.caption,
                        color = colors.mutedForeground,
                        modifier = Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
                    )
                }
                results.forEach { profile ->
                    UserRow(pubkey = profile.pubkey, onClick = { actions.onAddPerson(profile.pubkey) })
                }

                // Location
                SectionHeader(stringResource(R.string.create_location))
                LuminaTextField(
                    value = state.location,
                    onValueChange = actions.onLocationChange,
                    placeholder = stringResource(R.string.create_location_placeholder),
                    leadingGlyph = LucideGlyphs.MapPin,
                    enabled = inputsEnabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                )

                // Content warning
                Spacer(Modifier.height(8.dp))
                SettingsRow(
                    title = stringResource(R.string.create_cw),
                    subtitle = stringResource(R.string.create_cw_subtitle),
                    glyph = LucideGlyphs.EyeOff,
                    onClick = if (inputsEnabled) ({ actions.onContentWarningChange(!state.contentWarning) }) else null,
                    trailing = {
                        LuminaSwitch(
                            checked = state.contentWarning,
                            onCheckedChange = actions.onContentWarningChange,
                            enabled = inputsEnabled,
                        )
                    },
                )
                if (state.contentWarning) {
                    LuminaTextField(
                        value = state.contentWarningReason,
                        onValueChange = actions.onContentWarningReasonChange,
                        placeholder = stringResource(R.string.create_cw_reason),
                        enabled = inputsEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun PublishStatus(state: CreateUiState, actions: CreateActions) {
    val colors = LuminaTheme.colors
    when (state.publish) {
        PublishState.Idle -> Unit
        PublishState.Signing -> StatusBanner {
            Spinner(size = 18.dp)
            Text(stringResource(R.string.create_waiting_signer), style = LuminaTheme.typography.body, color = colors.foreground)
        }
        is PublishState.Sending -> StatusBanner {
            Spinner(size = 18.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.create_sending), style = LuminaTheme.typography.body, color = colors.foreground)
                Text(stringResource(R.string.create_sending_hint), style = LuminaTheme.typography.caption, color = colors.mutedForeground)
            }
        }
        is PublishState.Failed -> StatusBanner {
            LuminaIcon(LucideGlyphs.TriangleAlert, null, tint = colors.destructive, size = 18.dp)
            Text(
                stringResource(R.string.create_publish_failed),
                style = LuminaTheme.typography.body,
                color = colors.foreground,
                modifier = Modifier.weight(1f),
            )
            LuminaButton(
                text = stringResource(R.string.create_publish_retry),
                onClick = actions.onRetryPublish,
                style = ButtonStyle.Outline,
                leadingIcon = LucideGlyphs.RefreshCw,
            )
        }
    }
}

@Composable
private fun StatusBanner(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(LuminaTheme.colors.surfaceMuted)
                .heightIn(min = 48.dp)
                .padding(horizontal = LuminaDimens.gutter, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
        Hairline()
    }
}

@Composable
private fun UploadRow(index: Int, image: DraftImage, actions: CreateActions) {
    val colors = LuminaTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val stale = image.upload.key != null && image.upload.key != image.cropRect
    val upload = if (stale) UploadState.Idle else image.upload
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .padding(horizontal = LuminaDimens.gutter, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(LuminaDimens.avatarList).clip(LuminaShapes.control)) {
            CroppedImage(image.uri, image.imageAspect, image.thumbCrop, null, Modifier.fillMaxSize(), maxDecodeSize = 128)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.create_thumb_desc, index + 1),
                style = LuminaTheme.typography.username,
                color = colors.foreground,
            )
            val (text, color) = when (upload) {
                UploadState.Idle -> stringResource(R.string.create_upload_waiting) to colors.mutedForeground
                is UploadState.Preparing -> stringResource(R.string.create_upload_preparing) to colors.mutedForeground
                is UploadState.Uploading -> stringResource(R.string.create_upload_progress, (upload.progress * 100).toInt()) to colors.mutedForeground
                is UploadState.Done -> stringResource(R.string.create_upload_done) to colors.mutedForeground
                is UploadState.Failed -> upload.message.resolve(context) to colors.destructive
            }
            Text(text, style = LuminaTheme.typography.caption, color = color, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (upload is UploadState.Uploading || upload is UploadState.Preparing) {
                val progress = (upload as? UploadState.Uploading)?.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().clip(LuminaShapes.pill),
                        color = colors.accent,
                        trackColor = colors.surfaceMuted,
                        drawStopIndicator = {},
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(LuminaShapes.pill),
                        color = colors.accent,
                        trackColor = colors.surfaceMuted,
                    )
                }
            }
        }
        when (upload) {
            is UploadState.Failed -> LuminaIconButton(
                LucideGlyphs.RefreshCw,
                stringResource(R.string.create_upload_retry),
                onClick = { actions.onRetryUpload(image.id) },
            )
            is UploadState.Done -> LuminaIcon(LucideGlyphs.CircleCheck, stringResource(R.string.create_upload_done), tint = colors.accent, size = 22.dp)
            else -> Unit
        }
    }
}

@Composable
private fun RemovableChip(text: String, removeLabel: String, onRemove: (() -> Unit)?) {
    val colors = LuminaTheme.colors
    Row(
        Modifier
            .clip(LuminaShapes.pill)
            .background(colors.surfaceMuted)
            .then(if (onRemove != null) Modifier.tapTarget(label = removeLabel, onClick = onRemove) else Modifier)
            .heightIn(min = 36.dp)
            .padding(start = 12.dp, end = if (onRemove != null) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = LuminaTheme.typography.username, color = colors.accent)
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            LuminaIcon(LucideGlyphs.X, null, size = 16.dp, tint = colors.mutedForeground)
        }
    }
}

@Composable
private fun PersonChip(pubkey: String, onRemove: (() -> Unit)?) {
    val colors = LuminaTheme.colors
    val name = Profile.shortNpub(pubkey)
    Row(
        Modifier
            .clip(LuminaShapes.pill)
            .background(colors.surfaceMuted)
            .then(if (onRemove != null) Modifier.tapTarget(label = stringResource(R.string.create_person_remove, name), onClick = onRemove) else Modifier)
            .heightIn(min = 36.dp)
            .padding(start = 4.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatar(pubkey, 28.dp)
        Spacer(Modifier.width(6.dp))
        UserName(pubkey, showBadge = false, modifier = Modifier.widthIn(max = 160.dp))
        if (onRemove != null) {
            Spacer(Modifier.width(4.dp))
            LuminaIcon(LucideGlyphs.X, null, size = 16.dp, tint = colors.mutedForeground)
        }
    }
}

// ---- Discard ----------------------------------------------------------------------------------------------------

@Composable
fun DiscardSheet(onDiscard: () -> Unit, onDismiss: () -> Unit) {
    LuminaBottomSheet(onDismiss = onDismiss, title = stringResource(R.string.create_discard_title)) {
        Text(
            stringResource(R.string.create_discard_message),
            style = LuminaTheme.typography.body,
            color = LuminaTheme.colors.mutedForeground,
            modifier = Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        SheetAction(stringResource(R.string.create_discard), LucideGlyphs.Trash2, onClick = onDiscard, destructive = true)
        SheetAction(stringResource(R.string.create_keep_editing), null, onClick = onDismiss)
    }
}

// ---- Previews ---------------------------------------------------------------------------------------------------

private fun previewImages(): List<DraftImage> = listOf(
    DraftImage("a", Uri.EMPTY, 4000, 3000, CropSpec(AspectPreset.Square), alt = "Sunset over the bay"),
    DraftImage("b", Uri.EMPTY, 3000, 4000),
    DraftImage("c", Uri.EMPTY, 1920, 1080, CropSpec(AspectPreset.Landscape)),
)

private fun previewDetails(): CreateUiState {
    val key = CropRect.Full
    val done = UploadedImage("https://blossom.example/a.jpg", "image/jpeg", 2048, 2048, "a".repeat(64), 1000, null)
    val images = previewImages()
    return CreateUiState(
        ready = true,
        loggedIn = true,
        step = CreateStep.Details,
        images = listOf(
            images[0].copy(upload = UploadState.Done(done, images[0].cropRect)),
            images[1].copy(upload = UploadState.Uploading(0.42f, key)),
            images[2].copy(upload = UploadState.Failed(rocks.lumina.app.core.ui.util.UiMessage.Res(R.string.create_upload_failed_servers), images[2].cropRect)),
        ),
        caption = "Golden hour at the pier #sunset #sf",
        extraHashtags = listOf("photography"),
        people = listOf("82341f882b6eabcd2ba7f1ef90aad961cf074af15b9ef44a09f9d2a8fbfbe6a2"),
        location = "San Francisco",
        contentWarning = true,
        publish = PublishState.Signing,
    )
}

@Preview(name = "Pick light")
@Composable
private fun PickLightPreview() {
    LuminaTheme(dark = false) { CreateScreen(CreateUiState(ready = true, loggedIn = true), CreateActions()) }
}

@Preview(name = "Pick dark")
@Composable
private fun PickDarkPreview() {
    LuminaTheme(dark = true) { CreateScreen(CreateUiState(ready = true, loggedIn = true), CreateActions()) }
}

@Preview(name = "Logged out light")
@Composable
private fun LoggedOutLightPreview() {
    LuminaTheme(dark = false) { CreateScreen(CreateUiState(ready = true, loggedIn = false), CreateActions()) }
}

@Preview(name = "Logged out dark")
@Composable
private fun LoggedOutDarkPreview() {
    LuminaTheme(dark = true) { CreateScreen(CreateUiState(ready = true, loggedIn = false), CreateActions()) }
}

@Preview(name = "Edit light", heightDp = 900)
@Composable
private fun EditLightPreview() {
    LuminaTheme(dark = false) {
        CreateScreen(CreateUiState(ready = true, loggedIn = true, step = CreateStep.Edit, images = previewImages()), CreateActions())
    }
}

@Preview(name = "Edit dark", heightDp = 900)
@Composable
private fun EditDarkPreview() {
    LuminaTheme(dark = true) {
        CreateScreen(CreateUiState(ready = true, loggedIn = true, step = CreateStep.Edit, images = previewImages(), selected = 1), CreateActions())
    }
}

@Preview(name = "Details light", heightDp = 1400)
@Composable
private fun DetailsLightPreview() {
    LuminaTheme(dark = false) { CreateScreen(previewDetails(), CreateActions()) }
}

@Preview(name = "Details dark", heightDp = 1400)
@Composable
private fun DetailsDarkPreview() {
    LuminaTheme(dark = true) { CreateScreen(previewDetails().copy(publish = PublishState.Idle), CreateActions()) }
}

@Preview(name = "Loading")
@Composable
private fun LoadingPreview() {
    LuminaTheme(dark = false) { CreateScreen(CreateUiState(ready = false), CreateActions()) }
}
