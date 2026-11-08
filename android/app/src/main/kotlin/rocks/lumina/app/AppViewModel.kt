package rocks.lumina.app

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import rocks.lumina.app.core.data.AppTheme
import rocks.lumina.app.core.data.NotificationRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.theme.ThemeMode
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.signer.SignerMonitor

/** App-shell state: theme, active account, nav badges, login requests, signer activity, pending intents. */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppViewModel @Inject constructor(
    private val accountManager: AccountManager,
    settings: SettingsRepository,
    notifications: NotificationRepository,
    signerMonitor: SignerMonitor,
) : ViewModel() {
    val ready: StateFlow<Boolean> = accountManager.ready

    val themeMode: StateFlow<ThemeMode> = settings.settings.map {
        when (it.theme) {
            AppTheme.SYSTEM -> ThemeMode.SYSTEM
            AppTheme.LIGHT -> ThemeMode.LIGHT
            AppTheme.DARK -> ThemeMode.DARK
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    val activePubkey: StateFlow<String?> = accountManager.active.map { it?.pubkey }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val unreadNotifications: StateFlow<Int> = notifications.unread

    val loginRequests: SharedFlow<Unit> = accountManager.loginRequests

    /** True while a signer request has been pending for a moment (remote/Amber approval). */
    val signerWaiting: StateFlow<Boolean> = signerMonitor.pending.map { it > 0 }.debounce(700)
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _incoming = MutableStateFlow<IncomingIntent?>(null)
    val incoming: StateFlow<IncomingIntent?> = _incoming.asStateFlow()

    private val _sharedUris = MutableStateFlow<List<Uri>>(emptyList())
    val sharedUris: StateFlow<List<Uri>> = _sharedUris.asStateFlow()

    fun onIntent(intent: IncomingIntent?) {
        if (intent is IncomingIntent.Share) _sharedUris.value = intent.uris
        _incoming.value = intent
    }

    fun consumeIncoming() {
        _incoming.value = null
    }

    fun consumeShared() {
        _sharedUris.value = emptyList()
    }
}
