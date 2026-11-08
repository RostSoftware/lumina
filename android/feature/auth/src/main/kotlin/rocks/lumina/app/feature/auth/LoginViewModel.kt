package rocks.lumina.app.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vitorpamplona.quartz.nip55AndroidSigner.api.SignerResult
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import com.vitorpamplona.quartz.nip55AndroidSigner.client.getExternalSignersInstalled
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.signer.NostrConnectSession
import rocks.lumina.app.core.signer.SignerFailure
import rocks.lumina.app.core.signer.toSignerFailure
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

enum class LoginStep { Choose, Bunker, NostrConnect, Nsec, Creating, Profile, Backup }

/** An installed NIP-55 signer app. */
data class SignerApp(val packageName: String, val label: String)

data class LoginUiState(
    val step: LoginStep = LoginStep.Choose,
    val signerApps: List<SignerApp> = emptyList(),
    val amberBusy: Boolean = false,
    // bunker
    val bunkerInput: String = "",
    val bunkerInvalid: Boolean = false,
    val bunkerWaiting: Boolean = false,
    // nostrconnect
    val nostrConnectUri: String? = null,
    val nostrConnectFailed: Boolean = false,
    // nsec
    val nsecInput: String = "",
    val nsecInvalid: Boolean = false,
    val nsecBusy: Boolean = false,
    // new account
    val newPubkey: String? = null,
    val displayName: String = "",
    val about: String = "",
    val profileSaving: Boolean = false,
    val backupNsec: String? = null,
    val backupRevealed: Boolean = false,
    val backupCopied: Boolean = false,
) {
    val signerInstalled: Boolean get() = signerApps.isNotEmpty()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountManager: AccountManager,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _done = Channel<Unit>(Channel.CONFLATED)
    /** Emits once login (and onboarding for new accounts) is finished. */
    val done: Flow<Unit> = _done.receiveAsFlow()

    private var job: Job? = null

    init {
        refreshSigners()
    }

    /** Re-detects NIP-55 signers (called on resume: the user may just have installed Amber). */
    fun refreshSigners() {
        viewModelScope.launch {
            val apps = withContext(Dispatchers.IO) {
                runCatching {
                    val pm = context.packageManager
                    getExternalSignersInstalled(context).mapNotNull { info ->
                        val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                        SignerApp(pkg, info.loadLabel(pm).toString())
                    }.distinctBy { it.packageName }
                }.getOrDefault(emptyList())
            }
            _state.update { it.copy(signerApps = apps) }
        }
    }

    fun open(step: LoginStep) {
        when (step) {
            LoginStep.NostrConnect -> startNostrConnect()
            else -> _state.update { it.copy(step = step) }
        }
    }

    /** Back inside the flow: cancels pending work and returns to the chooser. Returns false when there is nothing to go back to. */
    fun back(): Boolean {
        val step = _state.value.step
        return when (step) {
            LoginStep.Choose -> false
            LoginStep.Bunker, LoginStep.NostrConnect, LoginStep.Nsec -> {
                cancelPending()
                _state.update { it.copy(step = LoginStep.Choose, nostrConnectUri = null, nostrConnectFailed = false) }
                true
            }
            LoginStep.Creating -> true
            LoginStep.Profile -> {
                skipProfile()
                true
            }
            LoginStep.Backup -> {
                finish()
                true
            }
        }
    }

    private fun cancelPending() {
        job?.cancel()
        job = null
        _state.update { it.copy(bunkerWaiting = false, nsecBusy = false) }
    }

    private fun message(message: UiMessage) {
        _messages.trySend(message)
    }

    private fun finish() {
        _done.trySend(Unit)
    }

    // ---- Amber / NIP-55 -------------------------------------------------------------------

    fun amberLaunchFailed() {
        _state.update { it.copy(amberBusy = false) }
        message(UiMessage.Res(R.string.auth_no_app))
    }

    fun amberLaunched() {
        _state.update { it.copy(amberBusy = true) }
    }

    /** Handles the `get_public_key` activity result. [data] is null when the user backed out. */
    fun onAmberResult(ok: Boolean, data: android.content.Intent?) {
        if (!ok || data == null) {
            _state.update { it.copy(amberBusy = false) }
            message(UiMessage.Res(R.string.auth_amber_rejected))
            return
        }
        val result = runCatching { ExternalSignerLogin.parseResult(data) }.getOrNull()
        if (result !is SignerResult.RequestAddressed.Successful) {
            _state.update { it.copy(amberBusy = false) }
            message(
                if (result is SignerResult.RequestAddressed.ManuallyRejected || result is SignerResult.RequestAddressed.AutomaticallyRejected) {
                    UiMessage.Res(R.string.auth_amber_rejected)
                } else {
                    UiMessage.Res(R.string.auth_amber_failed)
                },
            )
            return
        }
        viewModelScope.launch {
            try {
                accountManager.loginWithAmber(result.result.pubkey, result.result.packageName)
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(UiMessage.Res(R.string.auth_amber_failed))
            } finally {
                _state.update { it.copy(amberBusy = false) }
            }
        }
    }

    // ---- NIP-46 bunker --------------------------------------------------------------------

    fun setBunkerInput(value: String) {
        _state.update { it.copy(bunkerInput = value, bunkerInvalid = false) }
    }

    fun onScanned(content: String?) {
        if (content == null) return // scanner closed
        val uri = LoginInputs.cleanScanned(content)
        if (uri == null) {
            message(UiMessage.Res(R.string.auth_scan_wrong))
            return
        }
        setBunkerInput(uri)
        connectBunker()
    }

    fun connectBunker() {
        val input = _state.value.bunkerInput.trim()
        if (!LoginInputs.isBunkerUri(input)) {
            _state.update { it.copy(bunkerInvalid = true) }
            return
        }
        if (_state.value.bunkerWaiting) return
        _state.update { it.copy(bunkerWaiting = true) }
        job = viewModelScope.launch {
            try {
                accountManager.loginWithBunker(input)
                finish()
            } catch (e: TimeoutCancellationException) {
                message(SignerFailure.TIMED_OUT.toUiMessage())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(bunkerInvalid = true) }
            } catch (e: Exception) {
                message(remoteFailure(e, R.string.auth_bunker_failed))
            } finally {
                _state.update { it.copy(bunkerWaiting = false) }
            }
        }
    }

    fun cancelBunker() = cancelPending()

    // ---- NIP-46 nostrconnect --------------------------------------------------------------

    fun startNostrConnect() {
        job?.cancel()
        val session: NostrConnectSession = accountManager.newNostrConnectSession()
        _state.update { it.copy(step = LoginStep.NostrConnect, nostrConnectUri = session.uri, nostrConnectFailed = false) }
        job = viewModelScope.launch {
            try {
                accountManager.completeNostrConnect(session)
                finish()
            } catch (e: TimeoutCancellationException) {
                _state.update { it.copy(nostrConnectFailed = true) }
                message(SignerFailure.TIMED_OUT.toUiMessage())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(nostrConnectFailed = true) }
                message(remoteFailure(e, R.string.auth_nc_failed))
            }
        }
    }

    // ---- nsec ----------------------------------------------------------------------------

    fun setNsecInput(value: String) {
        _state.update { it.copy(nsecInput = value, nsecInvalid = false) }
    }

    fun loginWithNsec() {
        val input = _state.value.nsecInput.trim()
        if (!LoginInputs.looksLikeSecretKey(input)) {
            _state.update { it.copy(nsecInvalid = true) }
            return
        }
        if (_state.value.nsecBusy) return
        _state.update { it.copy(nsecBusy = true) }
        job = viewModelScope.launch {
            try {
                accountManager.loginWithNsec(input)
                _state.update { it.copy(nsecInput = "") }
                finish()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(nsecInvalid = true) }
            } catch (e: Exception) {
                message(e.toUiMessage())
            } finally {
                _state.update { it.copy(nsecBusy = false) }
            }
        }
    }

    // ---- new account ---------------------------------------------------------------------

    fun createAccount() {
        if (_state.value.step == LoginStep.Creating) return
        _state.update { it.copy(step = LoginStep.Creating) }
        viewModelScope.launch {
            try {
                val account = accountManager.createAccount()
                _state.update { it.copy(step = LoginStep.Profile, newPubkey = account.pubkey) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(step = LoginStep.Choose) }
                message(e.toUiMessage())
            }
        }
    }

    fun setDisplayName(value: String) = _state.update { it.copy(displayName = value) }

    fun setAbout(value: String) = _state.update { it.copy(about = value) }

    fun saveProfile() {
        val s = _state.value
        val pubkey = s.newPubkey ?: return skipProfile()
        val changes = LoginInputs.profileChanges(s.displayName, s.about)
        if (changes.isEmpty()) return skipProfile()
        if (s.profileSaving) return
        _state.update { it.copy(profileSaving = true) }
        viewModelScope.launch {
            try {
                profiles.updateProfile(pubkey, changes)
                message(UiMessage.Res(R.string.auth_welcome))
                toBackup(pubkey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message(e.toUiMessage())
            } finally {
                _state.update { it.copy(profileSaving = false) }
            }
        }
    }

    fun skipProfile() {
        val pubkey = _state.value.newPubkey ?: return finish()
        toBackup(pubkey)
    }

    private fun toBackup(pubkey: String) {
        val nsec = accountManager.exportNsec(pubkey)
        _state.update { it.copy(step = LoginStep.Backup, backupNsec = nsec, backupRevealed = false) }
    }

    fun revealBackup() = _state.update { it.copy(backupRevealed = true) }

    fun backupCopied() = _state.update { it.copy(backupCopied = true) }

    fun finishBackup() {
        _state.update { it.copy(backupNsec = null, backupRevealed = false) }
        finish()
    }

    private fun remoteFailure(e: Throwable, fallback: Int): UiMessage =
        if (e.toSignerFailure() == SignerFailure.OTHER) UiMessage.Res(fallback) else e.toUiMessage()

    override fun onCleared() {
        job?.cancel()
        super.onCleared()
    }
}
