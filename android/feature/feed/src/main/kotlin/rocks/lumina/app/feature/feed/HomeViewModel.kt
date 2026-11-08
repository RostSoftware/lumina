package rocks.lumina.app.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.ContactRepository
import rocks.lumina.app.core.data.DmRepository
import rocks.lumina.app.core.data.FeedRepository
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.signer.AccountManager

enum class FeedEmpty { LOGGED_OUT, NO_FOLLOWS, NO_POSTS }

data class HomeUiState(
    val loading: Boolean = true,
    val posts: List<PicturePost> = emptyList(),
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: Boolean = false,
    val empty: FeedEmpty? = null,
    val pendingNew: List<PicturePost> = emptyList(),
    val hasUnreadMessages: Boolean = false,
)

/** Following feed: kind 20 from the user's kind 3 contacts (outbox model), cache first. */
@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val feed: FeedRepository,
    private val contacts: ContactRepository,
    private val accountManager: AccountManager,
    dms: DmRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private var authors: Set<String> = emptySet()
    private var nextUntil: Long? = null
    private var loadJob: Job? = null
    private var liveJob: Job? = null

    init {
        viewModelScope.launch {
            combine(accountManager.active, contacts.following, contacts.loaded) { active, following, loaded ->
                Triple(active?.pubkey, following, loaded)
            }.debounce(300).distinctUntilChanged().collect { (pubkey, following, loaded) ->
                when {
                    pubkey == null -> {
                        authors = emptySet()
                        liveJob?.cancel()
                        _state.value = HomeUiState(loading = false, empty = FeedEmpty.LOGGED_OUT)
                    }
                    !loaded -> _state.update { it.copy(loading = it.posts.isEmpty()) }
                    following.isEmpty() -> {
                        authors = emptySet()
                        liveJob?.cancel()
                        _state.value = HomeUiState(loading = false, empty = FeedEmpty.NO_FOLLOWS)
                    }
                    following != authors -> {
                        authors = following
                        reload(showCache = true)
                    }
                }
            }
        }
        viewModelScope.launch {
            dms.hasUnread.collect { unread -> _state.update { it.copy(hasUnreadMessages = unread) } }
        }
    }

    private fun reload(showCache: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (showCache) {
                val cached = runCatching { feed.cachedFollowing(authors) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) _state.update { it.copy(posts = cached, loading = false, empty = null) }
            }
            val result = runCatching { feed.following(authors, until = null) }
            result.onSuccess { page ->
                nextUntil = page.nextUntil
                val merged = (page.posts + if (showCache) _state.value.posts else emptyList()).distinctBy { it.id }.sortedByDescending { it.createdAt }
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        posts = merged,
                        endReached = page.nextUntil == null,
                        error = false,
                        empty = if (merged.isEmpty()) FeedEmpty.NO_POSTS else null,
                        pendingNew = emptyList(),
                    )
                }
                startLive(merged.maxOfOrNull { it.createdAt })
            }.onFailure {
                _state.update { s -> s.copy(loading = false, refreshing = false, error = s.posts.isEmpty()) }
            }
        }
    }

    private fun startLive(since: Long?) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            feed.newPosts(authors, null, since = (since ?: (System.currentTimeMillis() / 1000)) + 1).collect { post ->
                _state.update { s ->
                    if (s.posts.any { it.id == post.id } || s.pendingNew.any { it.id == post.id }) s else s.copy(pendingNew = listOf(post) + s.pendingNew)
                }
            }
        }
    }

    fun refresh() {
        if (authors.isEmpty()) return
        _state.update { it.copy(refreshing = true) }
        reload(showCache = false)
    }

    fun retry() {
        _state.update { it.copy(loading = true, error = false) }
        reload(showCache = true)
    }

    /** Shows the posts collected by the "new posts" pill. */
    fun showPending() {
        _state.update { s -> s.copy(posts = (s.pendingNew + s.posts).distinctBy { it.id }.sortedByDescending { it.createdAt }, pendingNew = emptyList()) }
    }

    fun loadMore() {
        val until = nextUntil ?: return
        val s = _state.value
        if (s.loadingMore || s.endReached || s.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            runCatching { feed.following(authors, until) }
                .onSuccess { page ->
                    nextUntil = page.nextUntil
                    _state.update { it.copy(posts = (it.posts + page.posts).distinctBy { p -> p.id }, loadingMore = false, endReached = page.nextUntil == null) }
                }
                .onFailure { _state.update { it.copy(loadingMore = false) } }
        }
    }

    fun requestLogin() = accountManager.requestLogin()
}
