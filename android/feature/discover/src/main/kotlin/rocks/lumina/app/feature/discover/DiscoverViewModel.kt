package rocks.lumina.app.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.FeedRepository
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.nostr.model.PicturePost

/** The post grid (global or one hashtag). */
data class GridState(
    val posts: List<PicturePost> = emptyList(),
    val nextUntil: Long? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val pending: List<PicturePost> = emptyList(),
)

/** Account search results for the current query. */
sealed interface AccountResults {
    data object Idle : AccountResults
    data object Loading : AccountResults
    data class Content(val pubkeys: List<String>) : AccountResults
    data object Error : AccountResults
}

enum class SearchTab { Accounts, Tags }

data class DiscoverUiState(
    val query: String = "",
    val searchTab: SearchTab = SearchTab.Accounts,
    /** Normalised hashtag shown in hashtag mode; null = global Explore grid. */
    val hashtag: String? = null,
    val grid: GridState = GridState(),
    val accounts: AccountResults = AccountResults.Idle,
) {
    val searching: Boolean get() = query.isNotBlank()
    val tagSuggestion: String? get() = DiscoverLogic.normalizeHashtag(query)
}

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val feed: FeedRepository,
    private val profiles: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var started = false
    private var loadJob: Job? = null
    private var liveJob: Job? = null

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.map { it.query.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS).collect { q -> search(q) }
        }
    }

    private fun search(q: String) {
        searchJob?.cancel()
        if (q.isEmpty()) {
            _state.update { it.copy(accounts = AccountResults.Idle) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(accounts = AccountResults.Loading) }
            val result = try {
                AccountResults.Content(profiles.search(q).map { it.pubkey })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                AccountResults.Error
            }
            _state.update { if (it.query.trim() == q) it.copy(accounts = result) else it }
        }
    }

    /** Called once by the route with the navigation argument. */
    fun start(initialHashtag: String?) {
        if (started) return
        started = true
        showGrid(DiscoverLogic.normalizeHashtag(initialHashtag) ?: initialHashtag?.trim()?.removePrefix("#")?.lowercase()?.takeIf { it.isNotBlank() })
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value.take(MAX_QUERY)) }
    }

    fun onSearchTab(tab: SearchTab) {
        _state.update { it.copy(searchTab = tab) }
    }

    fun retrySearch() {
        search(_state.value.query.trim())
    }

    /** IME "search": a `#tag` query opens the hashtag grid directly. */
    fun onSubmit() {
        val q = _state.value.query.trim()
        if (q.startsWith("#")) DiscoverLogic.normalizeHashtag(q)?.let(::openHashtag)
    }

    fun openHashtag(tag: String) {
        val normalized = DiscoverLogic.normalizeHashtag(tag) ?: return
        _state.update { it.copy(query = "") }
        showGrid(normalized)
    }

    /** Leaves hashtag mode / search back to the global grid. */
    fun clear() {
        val s = _state.value
        if (s.searching) {
            _state.update { it.copy(query = "") }
            return
        }
        if (s.hashtag != null) showGrid(null)
    }

    private fun showGrid(hashtag: String?) {
        _state.update { it.copy(hashtag = hashtag, grid = GridState()) }
        load(refresh = false)
    }

    private fun load(refresh: Boolean) {
        val hashtag = _state.value.hashtag
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!refresh) {
                val cached = runCatching { feed.cachedDiscover(hashtag) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) updateGrid(hashtag) { it.copy(posts = cached, loading = false) }
            }
            try {
                val page = feed.discover(hashtag, until = null)
                updateGrid(hashtag) {
                    val posts = if (refresh || it.posts.isEmpty()) page.posts else DiscoverLogic.prepend(page.posts, it.posts)
                    it.copy(posts = posts, nextUntil = page.nextUntil, loading = false, refreshing = false, error = false, pending = emptyList())
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateGrid(hashtag) { it.copy(loading = false, refreshing = false, error = it.posts.isEmpty()) }
            }
        }
        listenForNew(hashtag)
    }

    private fun listenForNew(hashtag: String?) {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            runCatching {
                feed.newPosts(null, hashtag).collect { post ->
                    updateGrid(hashtag) { g ->
                        if (g.posts.any { it.id == post.id } || g.pending.any { it.id == post.id }) g else g.copy(pending = listOf(post) + g.pending)
                    }
                }
            }
        }
    }

    fun refresh() {
        _state.update { it.copy(grid = it.grid.copy(refreshing = true, error = false)) }
        load(refresh = true)
    }

    fun retry() {
        _state.update { it.copy(grid = it.grid.copy(loading = true, error = false)) }
        load(refresh = false)
    }

    fun showPending() {
        _state.update { s -> s.copy(grid = s.grid.copy(posts = DiscoverLogic.prepend(s.grid.pending, s.grid.posts), pending = emptyList())) }
    }

    fun loadMore() {
        val s = _state.value
        val grid = s.grid
        val until = grid.nextUntil ?: return
        if (grid.loading || grid.loadingMore || grid.refreshing) return
        val hashtag = s.hashtag
        updateGrid(hashtag) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = feed.discover(hashtag, until)
                updateGrid(hashtag) { it.copy(posts = DiscoverLogic.appendPage(it.posts, page.posts), nextUntil = page.nextUntil, loadingMore = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the cursor so scrolling retries; just stop the spinner.
                updateGrid(hashtag) { it.copy(loadingMore = false) }
            }
        }
    }

    /** Only applies updates while the grid still shows [hashtag] (results of a stale load are dropped). */
    private inline fun updateGrid(hashtag: String?, crossinline block: (GridState) -> GridState) {
        _state.update { s -> if (s.hashtag == hashtag) s.copy(grid = block(s.grid)) else s }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
        const val MAX_QUERY = 100
    }
}
