package rocks.lumina.app.feature.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.NotificationGroup
import rocks.lumina.app.core.data.NotificationRepository
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.signer.AccountManager

sealed interface NotificationsContent {
    data object LoggedOut : NotificationsContent
    data object Loading : NotificationsContent
    data object Empty : NotificationsContent
    data object Error : NotificationsContent
    data class Loaded(
        val newGroups: List<NotificationGroup>,
        val earlierGroups: List<NotificationGroup>,
        val posts: Map<String, PicturePost>,
    ) : NotificationsContent
}

data class NotificationsUiState(
    val content: NotificationsContent = NotificationsContent.Loading,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** System notifications are enabled in settings (drives the permission prompt banner). */
    val systemNotificationsEnabled: Boolean = true,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val accountManager: AccountManager,
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    private var groups: List<NotificationGroup> = emptyList()
    private var posts: Map<String, PicturePost> = emptyMap()
    private var nextUntil: Long? = null
    private var pubkey: String? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            accountManager.active.map { it?.pubkey }.distinctUntilChanged().collect { pk ->
                pubkey = pk
                groups = emptyList()
                posts = emptyMap()
                nextUntil = null
                loadJob?.cancel()
                if (pk == null) {
                    _state.value = NotificationsUiState(content = NotificationsContent.LoggedOut)
                } else {
                    _state.value = NotificationsUiState(content = NotificationsContent.Loading)
                    loadFirst(pk, refreshing = false)
                }
            }
        }
        viewModelScope.launch {
            settings.settings.map { it.systemNotifications }.distinctUntilChanged().collect { enabled ->
                _state.update { it.copy(systemNotificationsEnabled = enabled) }
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun refresh() {
        val pk = pubkey ?: return
        loadFirst(pk, refreshing = true)
    }

    fun retry() {
        val pk = pubkey ?: return
        _state.update { it.copy(content = NotificationsContent.Loading) }
        loadFirst(pk, refreshing = false)
    }

    private fun loadFirst(pk: String, refreshing: Boolean) {
        loadJob?.cancel()
        _state.update { it.copy(refreshing = refreshing) }
        loadJob = viewModelScope.launch {
            val result = runCatching { repository.page(pk, until = null) }
            if (pubkey != pk) return@launch
            result.onSuccess { page ->
                groups = page.groups
                posts = page.posts
                nextUntil = page.nextUntil
                profiles.prefetch(page.groups.flatMap { it.actors.take(2) }.distinct())
                publish(loadMoreFailed = false)
                // Unread state for this page was computed against the old "last seen"; now mark it seen
                // so the nav badge clears and the next visit starts fresh.
                runCatching { repository.markSeen(pk) }
            }.onFailure {
                if (groups.isEmpty()) {
                    _state.update { it.copy(content = NotificationsContent.Error, refreshing = false) }
                } else {
                    _state.update { it.copy(refreshing = false) }
                }
            }
        }
    }

    fun loadMore() {
        val pk = pubkey ?: return
        val until = nextUntil ?: return
        val current = _state.value
        if (current.loadingMore || current.refreshing || loadJob?.isActive == true) return
        _state.update { it.copy(loadingMore = true, loadMoreFailed = false) }
        loadJob = viewModelScope.launch {
            val result = runCatching { repository.page(pk, until = until) }
            if (pubkey != pk) return@launch
            result.onSuccess { page ->
                groups = NotificationGroups.merge(groups, page.groups)
                posts = posts + page.posts
                nextUntil = page.nextUntil
                profiles.prefetch(page.groups.flatMap { it.actors.take(2) }.distinct())
                publish(loadMoreFailed = false)
            }.onFailure {
                _state.update { it.copy(loadingMore = false, loadMoreFailed = true) }
            }
        }
    }

    private fun publish(loadMoreFailed: Boolean) {
        val (fresh, earlier) = NotificationGroups.sections(groups)
        _state.update {
            it.copy(
                // An empty page with a cursor still shows the list so its footer keeps paging older events.
                content = if (groups.isEmpty() && nextUntil == null) NotificationsContent.Empty else NotificationsContent.Loaded(fresh, earlier, posts),
                refreshing = false,
                loadingMore = false,
                canLoadMore = nextUntil != null,
                loadMoreFailed = loadMoreFailed,
            )
        }
    }
}
