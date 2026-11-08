package rocks.lumina.app.feature.create

import androidx.compose.ui.res.stringResource
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import rocks.lumina.app.core.ui.util.LocalSnackbarHostState
import rocks.lumina.app.core.ui.util.MessageEffect

/**
 * Full-screen create flow (no bottom bar): pick → edit (reorder, crop, alt) → details → upload → publish kind 20.
 * [sharedUris] are images received from the share sheet and are preselected once.
 */
@Composable
fun CreateRoute(sharedUris: List<Uri>, onClose: () -> Unit, onPublished: () -> Unit) {
    val viewModel: CreateViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val currentOnPublished by rememberUpdatedState(onPublished)

    LaunchedEffect(sharedUris) { viewModel.consumeShared(sharedUris) }
    LaunchedEffect(viewModel) { viewModel.published.collect { currentOnPublished() } }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = CreateUiState.MAX_IMAGES),
    ) { uris -> viewModel.addImages(uris) }

    val noCameraText = stringResource(R.string.create_no_camera)
    var pendingCamera by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingCamera?.let(Uri::parse)
        pendingCamera = null
        if (ok && uri != null) viewModel.addImages(listOf(uri))
    }

    val launchCamera: () -> Unit = {
        scope.launch {
            val uri = viewModel.newCameraUri()
            if (uri == null) {
                snackbar.showSnackbar(noCameraText)
                return@launch
            }
            pendingCamera = uri.toString()
            try {
                takePicture.launch(uri)
            } catch (_: ActivityNotFoundException) {
                pendingCamera = null
                snackbar.showSnackbar(noCameraText)
            }
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else scope.launch { snackbar.showSnackbar(noCameraText) }
    }

    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var cropping by rememberSaveable { mutableStateOf<String?>(null) }

    val requestClose = {
        // Once the signed post is queued it finishes in the background, so leaving loses nothing.
        if (state.hasDraft && state.publish !is PublishState.Sending) showDiscard = true else onClose()
    }

    BackHandler(enabled = cropping == null) {
        when {
            state.step == CreateStep.Details && !state.busy && state.loggedIn -> viewModel.backToEdit()
            else -> requestClose()
        }
    }

    val actions = CreateActions(
        onClose = requestClose,
        onLogin = viewModel::requestLogin,
        onPickGallery = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onTakePhoto = {
            // CAMERA is declared in the merged manifest (QR scanner), so the system camera hand-off needs it granted.
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera()
            } else {
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        },
        onSelect = viewModel::select,
        onMove = viewModel::move,
        onRemove = viewModel::remove,
        onCrop = { cropping = it },
        onAltChange = viewModel::setAlt,
        onNext = viewModel::goToDetails,
        onBackToEdit = viewModel::backToEdit,
        onCaptionChange = viewModel::setCaption,
        onAddHashtag = viewModel::addHashtag,
        onRemoveHashtag = viewModel::removeHashtag,
        onPersonQueryChange = viewModel::setPersonQuery,
        onAddPerson = viewModel::addPerson,
        onRemovePerson = viewModel::removePerson,
        onLocationChange = viewModel::setLocation,
        onContentWarningChange = viewModel::setContentWarning,
        onContentWarningReasonChange = viewModel::setContentWarningReason,
        onRetryUpload = viewModel::retryUpload,
        onPublish = viewModel::publish,
        onRetryPublish = viewModel::retryPublish,
    )

    // The create flow sits above the app shell, so it hosts its own snackbar.
    CompositionLocalProvider(LocalSnackbarHostState provides snackbar) {
        MessageEffect(viewModel.messages)
        Box(Modifier.fillMaxSize()) {
            val croppingImage = cropping?.let { id -> state.images.firstOrNull { it.id == id } }
            if (croppingImage != null && state.loggedIn) {
                CropEditor(
                    image = croppingImage,
                    onCancel = { cropping = null },
                    onDone = { spec ->
                        viewModel.setCrop(croppingImage.id, spec)
                        cropping = null
                    },
                )
            } else {
                if (cropping != null) cropping = null
                CreateScreen(state = state, actions = actions)
            }
            SnackbarHost(
                snackbar,
                Modifier
                    .align(Alignment.BottomCenter)
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }
    }

    if (showDiscard) {
        DiscardSheet(
            onDiscard = {
                showDiscard = false
                viewModel.discard()
                onClose()
            },
            onDismiss = { showDiscard = false },
        )
    }
}
