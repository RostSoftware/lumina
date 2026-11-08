package rocks.lumina.app.feature.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.media.BlossomClient
import rocks.lumina.app.core.media.BlossomUploadException
import rocks.lumina.app.core.media.ImageProcessor
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

data class EditProfileUiState(
    val loading: Boolean = true,
    val loggedOut: Boolean = false,
    val pubkey: String? = null,
    val original: Map<ProfileField, String> = emptyMap(),
    val fields: Map<ProfileField, String> = emptyMap(),
    val errors: Map<ProfileField, FieldError> = emptyMap(),
    /** Upload progress (0..1) per image field while uploading. */
    val uploads: Map<ProfileField, Float> = emptyMap(),
    val saving: Boolean = false,
) {
    fun value(field: ProfileField): String = fields[field].orEmpty()
    val dirty: Boolean get() = ProfileLogic.changes(original, fields).isNotEmpty()
    val canSave: Boolean get() = !loading && !saving && uploads.isEmpty() && pubkey != null
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val accountManager: AccountManager,
    private val imageProcessor: ImageProcessor,
    private val blossom: BlossomClient,
) : ViewModel() {
    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits once the profile was published; the screen then closes. */
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        load()
    }

    fun load() {
        val pk = accountManager.activePubkey
        if (pk == null) {
            _state.value = EditProfileUiState(loading = false, loggedOut = true)
            return
        }
        val cached = profiles.peek(pk)
        _state.value = EditProfileUiState(loading = cached == null, pubkey = pk).withProfile(cached)
        viewModelScope.launch {
            val fresh = runCatching { profiles.refresh(pk) }.getOrNull()
            _state.update { s ->
                // Don't overwrite what the user is already typing.
                val untouched = s.fields == s.original
                val base = s.copy(loading = false)
                if (fresh != null && untouched) base.withProfile(fresh) else base
            }
        }
    }

    private fun EditProfileUiState.withProfile(profile: Profile?): EditProfileUiState {
        if (profile == null) return this
        fun raw(vararg keys: String): String =
            keys.firstNotNullOfOrNull { k -> (profile.raw[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }.orEmpty()
        val values = mapOf(
            ProfileField.Name to raw("name", "username"),
            ProfileField.DisplayName to raw("display_name", "displayName"),
            ProfileField.About to raw("about"),
            ProfileField.Picture to raw("picture"),
            ProfileField.Banner to raw("banner"),
            ProfileField.Website to raw("website"),
            ProfileField.Nip05 to raw("nip05"),
            ProfileField.Lud16 to raw("lud16"),
        )
        return copy(original = values, fields = values, errors = emptyMap())
    }

    fun onChange(field: ProfileField, value: String) {
        _state.update { it.copy(fields = it.fields + (field to value.take(MAX_LENGTH)), errors = it.errors - field) }
    }

    fun upload(field: ProfileField, uri: Uri) {
        if (field != ProfileField.Picture && field != ProfileField.Banner) return
        val signer = accountManager.signerOrNull()
        if (signer == null) {
            accountManager.requestLogin()
            return
        }
        if (field in _state.value.uploads) return
        _state.update { it.copy(uploads = it.uploads + (field to 0f)) }
        viewModelScope.launch {
            try {
                val size = imageProcessor.orientedSize(uri)
                val aspect = if (field == ProfileField.Picture) 1f else BANNER_ASPECT
                val crop = size?.let { (w, h) -> ProfileLogic.centerCrop(w, h, aspect) } ?: rocks.lumina.app.core.media.CropRect.Full
                val prepared = imageProcessor.prepare(uri, crop)
                val blob = blossom.upload(prepared, signer) { p ->
                    _state.update { s -> if (field in s.uploads) s.copy(uploads = s.uploads + (field to p.coerceIn(0f, 1f))) else s }
                }
                _state.update { it.copy(fields = it.fields + (field to blob.url), errors = it.errors - field) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: BlossomUploadException) {
                _messages.tryEmit(UiMessage.Res(R.string.profile_edit_upload_failed))
            } catch (_: IllegalArgumentException) {
                _messages.tryEmit(UiMessage.Res(R.string.profile_edit_image_unsupported))
            } catch (e: Exception) {
                _messages.tryEmit(e.toUiMessage())
            } finally {
                _state.update { it.copy(uploads = it.uploads - field) }
            }
        }
    }

    fun save() {
        val s = _state.value
        val pk = s.pubkey ?: return
        if (!s.canSave) return
        val changedKeys = ProfileLogic.changes(s.original, s.fields).keys
        val errors = ProfileField.entries
            .filter { it.key in changedKeys }
            .mapNotNull { f -> ProfileLogic.validate(f, s.value(f))?.let { f to it } }
            .toMap()
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            _messages.tryEmit(UiMessage.Res(R.string.profile_edit_fix_errors))
            return
        }
        val changes = ProfileLogic.changes(s.original, s.fields)
        if (changes.isEmpty()) {
            _saved.tryEmit(Unit)
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                profiles.updateProfile(pk, changes)
                _state.update { it.copy(saving = false, original = it.fields) }
                _saved.tryEmit(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _messages.tryEmit(e.toUiMessage())
            }
        }
    }

    fun requestLogin() {
        accountManager.requestLogin()
    }

    private companion object {
        const val MAX_LENGTH = 2_000
        const val BANNER_ASPECT = 3f
    }
}
