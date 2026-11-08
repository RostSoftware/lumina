package rocks.lumina.app.feature.create

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip01Core.core.Event
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rocks.lumina.app.core.data.PublishJobState
import rocks.lumina.app.core.data.PublishScheduler
import rocks.lumina.app.core.data.Publisher
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.media.BlossomClient
import rocks.lumina.app.core.media.BlossomUploadException
import rocks.lumina.app.core.media.CropRect
import rocks.lumina.app.core.media.ImageProcessor
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

enum class CreateStep { Pick, Edit, Details }

/** Upload of one image. [key] is the crop the upload was made for; a different crop means "stale". */
sealed interface UploadState {
    val key: CropRect?

    data object Idle : UploadState {
        override val key: CropRect? = null
    }

    data class Preparing(override val key: CropRect) : UploadState
    data class Uploading(val progress: Float, override val key: CropRect) : UploadState
    data class Done(val result: UploadedImage, override val key: CropRect) : UploadState
    data class Failed(val message: UiMessage, override val key: CropRect) : UploadState
}

data class DraftImage(
    val id: String,
    val uri: Uri,
    val width: Int,
    val height: Int,
    val crop: CropSpec = CropSpec(),
    val alt: String = "",
    val upload: UploadState = UploadState.Idle,
) {
    val imageAspect: Float get() = if (width > 0 && height > 0) width.toFloat() / height else 1f
    val cropRect: CropRect get() = CropMath.toCropRect(crop, width, height)
    val frameAspect: Float get() = CropMath.frameAspect(crop, imageAspect)

    /** Square crop around the same center/zoom, for square thumbnails. */
    val thumbCrop: CropSpec get() = CropMath.clamp(crop.copy(aspect = AspectPreset.Square), imageAspect)
    val uploaded: UploadedImage? get() = (upload as? UploadState.Done)?.takeIf { it.key == cropRect }?.result
    val uploadInFlight: Boolean
        get() = (upload is UploadState.Preparing || upload is UploadState.Uploading) && upload.key == cropRect
}

sealed interface PublishState {
    data object Idle : PublishState
    data object Signing : PublishState
    data class Sending(val event: Event) : PublishState
    data class Failed(val event: Event) : PublishState
}

data class CreateUiState(
    val ready: Boolean = false,
    val loggedIn: Boolean = false,
    val step: CreateStep = CreateStep.Pick,
    val images: List<DraftImage> = emptyList(),
    val selected: Int = 0,
    val caption: String = "",
    val extraHashtags: List<String> = emptyList(),
    val people: List<String> = emptyList(),
    val location: String = "",
    val contentWarning: Boolean = false,
    val contentWarningReason: String = "",
    val personQuery: String = "",
    val personResults: List<Profile> = emptyList(),
    val searchingPeople: Boolean = false,
    val publish: PublishState = PublishState.Idle,
) {
    val hashtags: List<String> get() = PictureTags.allHashtags(caption, extraHashtags)
    val captionHashtags: List<String> get() = PictureTags.captionHashtags(caption)
    val allUploaded: Boolean get() = images.isNotEmpty() && images.all { it.uploaded != null }
    val busy: Boolean get() = publish is PublishState.Signing || publish is PublishState.Sending
    val canPublish: Boolean get() = loggedIn && allUploaded && !busy
    val hasDraft: Boolean get() = images.isNotEmpty()
    val canAddMore: Boolean get() = images.size < MAX_IMAGES

    companion object {
        const val MAX_IMAGES = 10
    }
}

/** What survives process death (uploads restart on restore). */
@Serializable
private data class SavedDraft(
    val step: String,
    val images: List<SavedImage>,
    val selected: Int,
    val caption: String,
    val extraHashtags: List<String>,
    val people: List<String>,
    val location: String,
    val contentWarning: Boolean,
    val contentWarningReason: String,
    val sharedConsumed: Boolean,
)

@Serializable
private data class SavedImage(
    val id: String,
    val uri: String,
    val width: Int,
    val height: Int,
    val aspect: String,
    val zoom: Float,
    val cx: Float,
    val cy: Float,
    val alt: String,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CreateViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    private val accountManager: AccountManager,
    private val imageProcessor: ImageProcessor,
    private val blossom: BlossomClient,
    private val publisher: Publisher,
    private val scheduler: PublishScheduler,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val draft = MutableStateFlow(CreateUiState())
    private var sharedConsumed = false

    val state: StateFlow<CreateUiState> = combine(draft, accountManager.ready, accountManager.active) { s, ready, active ->
        s.copy(ready = ready, loggedIn = active != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), draft.value)

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _published = Channel<Unit>(Channel.CONFLATED)
    val published: Flow<Unit> = _published.receiveAsFlow()

    private val personQuery = MutableStateFlow("")
    private val uploadJobs = HashMap<String, Job>()
    private val uploadPermits = Semaphore(MAX_PARALLEL_UPLOADS)
    private var publishJob: Job? = null

    init {
        restore()
        viewModelScope.launch {
            personQuery.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged().collectLatest { q ->
                if (q.isBlank()) {
                    draft.update { it.copy(personResults = emptyList(), searchingPeople = false) }
                    return@collectLatest
                }
                draft.update { it.copy(searchingPeople = true) }
                val results = try {
                    withContext(Dispatchers.IO) { profiles.search(q, limit = 8) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
                draft.update { it.copy(personResults = results, searchingPeople = false) }
            }
        }
        // Uploads need the signer: when the user logs in while on Details, start them.
        viewModelScope.launch {
            accountManager.active.collect { if (it != null && draft.value.step == CreateStep.Details) ensureUploads() }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    // ---- Selection --------------------------------------------------------------------------------------------

    /** Share-sheet intake: added once per ViewModel (survives rotation and process death). */
    fun consumeShared(uris: List<Uri>) {
        if (sharedConsumed || uris.isEmpty()) return
        sharedConsumed = true
        save()
        addImages(uris)
    }

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val existing = draft.value.images.map { it.uri }.toSet()
            val fresh = uris.distinct().filterNot { it in existing }
            val room = CreateUiState.MAX_IMAGES - draft.value.images.size
            if (fresh.size > room) _messages.send(UiMessage.Res(R.string.create_too_many, listOf(CreateUiState.MAX_IMAGES)))
            val added = ArrayList<DraftImage>()
            for (uri in fresh.take(room.coerceAtLeast(0))) {
                val image = withContext(Dispatchers.IO) { readImage(uri) }
                if (image == null) {
                    _messages.send(UiMessage.Res(R.string.create_unreadable))
                } else {
                    added += image
                }
            }
            if (added.isEmpty()) return@launch
            draft.update { s ->
                s.copy(
                    images = s.images + added,
                    selected = s.images.size,
                    step = if (s.step == CreateStep.Pick) CreateStep.Edit else s.step,
                )
            }
            save()
        }
    }

    private suspend fun readImage(uri: Uri): DraftImage? {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        if (mime != null && !mime.startsWith("image/")) return null
        // Photo Picker grants survive process death only when persisted (no-op for other providers).
        runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val size = runCatching { imageProcessor.orientedSize(uri) }.getOrNull() ?: return null
        return DraftImage(id = UUID.randomUUID().toString(), uri = uri, width = size.first, height = size.second)
    }

    /** Creates an empty file for the camera to write into and returns its FileProvider uri. */
    suspend fun newCameraUri(): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, CAMERA_DIR).apply { mkdirs() }
            val file = File.createTempFile("IMG_", ".jpg", dir)
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    fun select(index: Int) {
        draft.update { it.copy(selected = index.coerceIn(0, (it.images.size - 1).coerceAtLeast(0))) }
    }

    fun move(index: Int, delta: Int) {
        draft.update { s ->
            val target = index + delta
            if (index !in s.images.indices || target !in s.images.indices) return@update s
            val list = s.images.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            s.copy(images = list, selected = target)
        }
        save()
    }

    fun remove(id: String) {
        uploadJobs.remove(id)?.cancel()
        val removed = draft.value.images.firstOrNull { it.id == id }
        draft.update { s ->
            val list = s.images.filterNot { it.id == id }
            s.copy(
                images = list,
                selected = s.selected.coerceIn(0, (list.size - 1).coerceAtLeast(0)),
                step = if (list.isEmpty()) CreateStep.Pick else s.step,
            )
        }
        removed?.let { deleteIfCameraFile(it.uri) }
        save()
    }

    fun setCrop(id: String, crop: CropSpec) {
        draft.update { s -> s.copy(images = s.images.map { if (it.id == id) it.copy(crop = CropMath.clamp(crop, it.imageAspect)) else it }) }
        val image = draft.value.images.firstOrNull { it.id == id } ?: return
        if (image.upload !is UploadState.Idle && image.upload.key != image.cropRect) {
            uploadJobs.remove(id)?.cancel()
            setUpload(id, UploadState.Idle)
            if (draft.value.step == CreateStep.Details) startUpload(id)
        }
        save()
    }

    fun setAlt(id: String, alt: String) {
        draft.update { s -> s.copy(images = s.images.map { if (it.id == id) it.copy(alt = alt.take(ALT_MAX)) else it }) }
        save()
    }

    // ---- Steps ------------------------------------------------------------------------------------------------

    fun goToDetails() {
        if (draft.value.images.isEmpty()) return
        draft.update { it.copy(step = CreateStep.Details) }
        ensureUploads()
        save()
    }

    fun backToEdit() {
        if (draft.value.busy) return
        draft.update { it.copy(step = if (it.images.isEmpty()) CreateStep.Pick else CreateStep.Edit) }
        save()
    }

    // ---- Details ----------------------------------------------------------------------------------------------

    fun setCaption(value: String) = edit { it.copy(caption = value.take(PictureTags.CAPTION_MAX)) }

    fun addHashtag(input: String): Boolean {
        val tag = PictureTags.normalizeHashtag(input) ?: return false
        edit { s -> if (tag in s.extraHashtags) s else s.copy(extraHashtags = s.extraHashtags + tag) }
        return true
    }

    fun removeHashtag(tag: String) = edit { it.copy(extraHashtags = it.extraHashtags - tag) }

    fun setLocation(value: String) = edit { it.copy(location = value.take(PictureTags.LOCATION_MAX)) }

    fun setContentWarning(enabled: Boolean) = edit { it.copy(contentWarning = enabled) }

    fun setContentWarningReason(value: String) = edit { it.copy(contentWarningReason = value.take(REASON_MAX)) }

    fun setPersonQuery(value: String) {
        draft.update { it.copy(personQuery = value) }
        personQuery.value = value
    }

    fun addPerson(pubkey: String) {
        edit { s -> s.copy(people = if (pubkey in s.people) s.people else s.people + pubkey, personQuery = "", personResults = emptyList()) }
        personQuery.value = ""
        profiles.prefetch(listOf(pubkey))
    }

    fun removePerson(pubkey: String) = edit { it.copy(people = it.people - pubkey) }

    private fun edit(block: (CreateUiState) -> CreateUiState) {
        draft.update(block)
        save()
    }

    // ---- Uploads ----------------------------------------------------------------------------------------------

    private fun ensureUploads() {
        if (accountManager.active.value == null) return
        draft.value.images.forEach { image ->
            val current = image.upload.key == image.cropRect &&
                (image.upload is UploadState.Done || image.upload is UploadState.Uploading || image.upload is UploadState.Preparing)
            if (!current) startUpload(image.id)
        }
    }

    fun retryUpload(id: String) = startUpload(id)

    private fun startUpload(id: String) {
        val image = draft.value.images.firstOrNull { it.id == id } ?: return
        uploadJobs.remove(id)?.cancel()
        val key = image.cropRect
        setUpload(id, UploadState.Preparing(key))
        uploadJobs[id] = viewModelScope.launch {
            try {
                uploadPermits.withPermit {
                    val prepared = imageProcessor.prepare(image.uri, key)
                    val signer = accountManager.requireSigner()
                    setUpload(id, UploadState.Uploading(0f, key))
                    var last = 0f
                    val blob = blossom.upload(prepared, signer) { p ->
                        if (p >= 1f || p - last >= 0.01f || p < last) {
                            last = p
                            setUpload(id, UploadState.Uploading(p, key))
                        }
                    }
                    val result = UploadedImage(
                        url = blob.url,
                        mimeType = prepared.mimeType,
                        width = prepared.width,
                        height = prepared.height,
                        sha256 = prepared.sha256,
                        size = prepared.size,
                        blurhash = prepared.blurhash,
                    )
                    setUpload(id, UploadState.Done(result, key))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = when (e) {
                    is BlossomUploadException -> UiMessage.Res(R.string.create_upload_failed_servers)
                    is IllegalArgumentException -> UiMessage.Res(R.string.create_unreadable)
                    else -> e.toUiMessage()
                }
                setUpload(id, UploadState.Failed(message, key))
            }
        }
    }

    private fun setUpload(id: String, upload: UploadState) {
        draft.update { s ->
            s.copy(
                images = s.images.map { img ->
                    // Ignore late progress from a superseded upload.
                    if (img.id != id || (upload.key != null && upload.key != img.cropRect)) img else img.copy(upload = upload)
                },
            )
        }
    }

    // ---- Publish ----------------------------------------------------------------------------------------------

    fun publish() {
        val s = state.value
        if (!s.loggedIn) {
            accountManager.requestLogin()
            return
        }
        if (!s.canPublish) return
        val uploaded = s.images.mapNotNull { img -> img.uploaded?.copy(alt = img.alt.trim()) }
        if (uploaded.size != s.images.size) return
        val tags = PictureTags.build(
            PictureDraft(
                caption = s.caption,
                images = uploaded,
                extraHashtags = s.extraHashtags,
                people = s.people,
                location = s.location,
                contentWarning = s.contentWarning,
                contentWarningReason = s.contentWarningReason,
            ),
        )
        publishJob?.cancel()
        publishJob = viewModelScope.launch {
            draft.update { it.copy(publish = PublishState.Signing) }
            val event = try {
                publisher.sign(Kinds.PICTURE, PictureTags.content(s.caption), tags)
            } catch (e: CancellationException) {
                draft.update { it.copy(publish = PublishState.Idle) }
                throw e
            } catch (e: Exception) {
                draft.update { it.copy(publish = PublishState.Idle) }
                _messages.send(e.toUiMessage())
                return@launch
            }
            send(event)
        }
    }

    /** Re-enqueues the already signed event after the background publish failed. */
    fun retryPublish() {
        val failed = draft.value.publish as? PublishState.Failed ?: return
        publishJob?.cancel()
        publishJob = viewModelScope.launch { send(failed.event) }
    }

    private suspend fun send(event: Event) {
        draft.update { it.copy(publish = PublishState.Sending(event)) }
        val outcome = try {
            val id = withContext(Dispatchers.IO) { scheduler.enqueue(event) }
            scheduler.observe(id).first { it == PublishJobState.SUCCEEDED || it == PublishJobState.FAILED }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PublishJobState.FAILED
        }
        if (outcome == PublishJobState.SUCCEEDED) {
            val images = draft.value.images
            draft.value = CreateUiState()
            savedState.remove<String>(KEY_DRAFT)
            images.forEach { deleteIfCameraFile(it.uri) }
            _published.send(Unit)
        } else {
            draft.update { it.copy(publish = PublishState.Failed(event)) }
            _messages.send(UiMessage.Res(R.string.create_publish_failed))
        }
    }

    // ---- Discard ----------------------------------------------------------------------------------------------

    fun discard() {
        publishJob?.cancel()
        uploadJobs.values.forEach { it.cancel() }
        uploadJobs.clear()
        val images = draft.value.images
        draft.value = CreateUiState()
        savedState.remove<String>(KEY_DRAFT)
        images.forEach { deleteIfCameraFile(it.uri) }
    }

    private fun deleteIfCameraFile(uri: Uri) {
        if (uri.authority != "${context.packageName}.fileprovider") return
        viewModelScope.launch(Dispatchers.IO) { runCatching { context.contentResolver.delete(uri, null, null) } }
    }

    // ---- Saved state ------------------------------------------------------------------------------------------

    private fun save() {
        val s = draft.value
        val saved = SavedDraft(
            step = s.step.name,
            images = s.images.map {
                SavedImage(it.id, it.uri.toString(), it.width, it.height, it.crop.aspect.name, it.crop.zoom, it.crop.centerX, it.crop.centerY, it.alt)
            },
            selected = s.selected,
            caption = s.caption,
            extraHashtags = s.extraHashtags,
            people = s.people,
            location = s.location,
            contentWarning = s.contentWarning,
            contentWarningReason = s.contentWarningReason,
            sharedConsumed = sharedConsumed,
        )
        savedState[KEY_DRAFT] = json.encodeToString(SavedDraft.serializer(), saved)
    }

    private fun restore() {
        val raw = savedState.get<String>(KEY_DRAFT) ?: return
        val saved = runCatching { json.decodeFromString(SavedDraft.serializer(), raw) }.getOrNull() ?: return
        sharedConsumed = saved.sharedConsumed
        val images = saved.images.map {
            DraftImage(
                id = it.id,
                uri = Uri.parse(it.uri),
                width = it.width,
                height = it.height,
                crop = CropSpec(
                    aspect = runCatching { AspectPreset.valueOf(it.aspect) }.getOrDefault(AspectPreset.Original),
                    zoom = it.zoom,
                    centerX = it.cx,
                    centerY = it.cy,
                ),
                alt = it.alt,
            )
        }
        val step = runCatching { CreateStep.valueOf(saved.step) }.getOrDefault(CreateStep.Pick)
        draft.value = CreateUiState(
            step = if (images.isEmpty()) CreateStep.Pick else step,
            images = images,
            selected = saved.selected.coerceIn(0, (images.size - 1).coerceAtLeast(0)),
            caption = saved.caption,
            extraHashtags = saved.extraHashtags,
            people = saved.people,
            location = saved.location,
            contentWarning = saved.contentWarning,
            contentWarningReason = saved.contentWarningReason,
        )
        if (draft.value.step == CreateStep.Details) ensureUploads()
    }

    override fun onCleared() {
        uploadJobs.values.forEach { it.cancel() }
        super.onCleared()
    }

    private companion object {
        const val KEY_DRAFT = "create_draft"
        const val CAMERA_DIR = "camera"
        const val MAX_PARALLEL_UPLOADS = 2
        const val SEARCH_DEBOUNCE_MS = 350L
        const val ALT_MAX = 500
        const val REASON_MAX = 100
    }
}
