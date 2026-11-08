package rocks.lumina.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.ContactRepository
import rocks.lumina.app.core.data.FeedRepository
import rocks.lumina.app.core.data.ListsRepository
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

enum class ProfileTab { Posts, Reposts, Tagged }

private data class Relation(val isFollowing: Boolean, val muted: Boolean, val isOwn: Boolean, val ownFollowing: Int)

/** One grid tab. [loaded] = at least one network page arrived. */
data class TabState(
    val posts: List<PicturePost> = emptyList(),
    val nextUntil: Long? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: Boolean = false,
    val loaded: Boolean = false,
)

data class ProfileUiState(
    val pubkey: String? = null,
    /** Own profile requested while logged out. */
    val loggedOut: Boolean = false,
    val isOwn: Boolean = false,
    val profile: Profile? = null,
    val profileLoading: Boolean = true,
    val followers: Int? = null,
    val followersCapped: Boolean = false,
    val following: Int? = null,
    val isFollowing: Boolean = false,
    val followBusy: Boolean = false,
    val muted: Boolean = false,
    val tab: ProfileTab = ProfileTab.Posts,
    val posts: TabState = TabState(loading = true),
    val reposts: TabState = TabState(),
    val tagged: TabState = TabState(),
    val refreshing: Boolean = false,
) {
    fun tabState(tab: ProfileTab): TabState = when (tab) {
        ProfileTab.Posts -> posts
        ProfileTab.Reposts -> reposts
        ProfileTab.Tagged -> tagged
    }

    val current: TabState get() = tabState(tab)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profiles: ProfileRepository,
    private val contacts: ContactRepository,
    private val feed: FeedRepository,
    private val lists: ListsRepository,
    private val accountManager: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var bound = false
    private var targetJob: Job? = null
    private val tabJobs = HashMap<ProfileTab, Job>()

    /** Binds the screen argument once: a pubkey/npub, or null for the active account (follows account switches). */
    fun bind(arg: String?) {
        if (bound) return
        bound = true
        val fixed = arg?.let { if (UrlSafety.isHex64(it)) it else NostrLinks.pubkeyOf(it) }
        val targets = if (arg != null) flowOf(fixed) else accountManager.active.map { it?.pubkey }
        viewModelScope.launch {
            targets.distinctUntilChanged().collectLatest { pk ->
                targetJob?.cancel()
                tabJobs.values.forEach { it.cancel() }
                tabJobs.clear()
                if (pk == null) {
                    _state.value = ProfileUiState(loggedOut = arg == null, profileLoading = false, posts = TabState())
                } else {
                    start(pk)
                }
            }
        }
    }

    private fun start(pk: String) {
        val own = pk == accountManager.activePubkey
        _state.value = ProfileUiState(pubkey = pk, isOwn = own, profile = profiles.peek(pk), posts = TabState(loading = true))
        targetJob = viewModelScope.launch {
            launch { profiles.observe(pk).collect { p -> if (p != null) update(pk) { it.copy(profile = p, profileLoading = false) } } }
            launch {
                combine(contacts.following, lists.mutes, accountManager.active) { following, mutes, active ->
                    Relation(pk in following, pk in mutes.pubkeys, active?.pubkey == pk, following.size)
                }.collect { r ->
                    update(pk) {
                        it.copy(
                            isFollowing = r.isFollowing,
                            muted = r.muted,
                            isOwn = r.isOwn,
                            following = if (r.isOwn && contacts.loaded.value) r.ownFollowing else it.following,
                        )
                    }
                }
            }
            launch { loadHeader(pk) }
        }
        loadTab(pk, ProfileTab.Posts, refresh = false)
    }

    private suspend fun loadHeader(pk: String) {
        val refreshed = runCatching { profiles.refresh(pk) }.getOrNull()
        update(pk) { it.copy(profile = refreshed ?: it.profile, profileLoading = false) }
        viewModelScope.launch {
            runCatching { contacts.followingOf(pk) }.onSuccess { list ->
                update(pk) { if (it.isOwn && contacts.loaded.value) it else it.copy(following = list.size) }
            }
        }
        viewModelScope.launch {
            runCatching { contacts.followersOf(pk, FOLLOWER_LIMIT) }.onSuccess { list ->
                update(pk) { it.copy(followers = list.size, followersCapped = list.size >= FOLLOWER_LIMIT) }
            }
        }
    }

    fun selectTab(tab: ProfileTab) {
        val s = _state.value
        _state.update { it.copy(tab = tab) }
        val pk = s.pubkey ?: return
        val ts = s.tabState(tab)
        if (!ts.loaded && !ts.loading) loadTab(pk, tab, refresh = false)
    }

    fun retryTab() {
        val s = _state.value
        val pk = s.pubkey ?: return
        loadTab(pk, s.tab, refresh = false)
    }

    fun refresh() {
        val s = _state.value
        val pk = s.pubkey ?: return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch { loadHeader(pk) }
        loadTab(pk, s.tab, refresh = true)
    }

    private fun loadTab(pk: String, tab: ProfileTab, refresh: Boolean) {
        tabJobs[tab]?.cancel()
        updateTab(pk, tab) { it.copy(loading = !refresh || it.posts.isEmpty(), error = false) }
        tabJobs[tab] = viewModelScope.launch {
            if (tab == ProfileTab.Posts && !refresh) {
                val cached = runCatching { feed.cachedUserPosts(pk) }.getOrDefault(emptyList())
                if (cached.isNotEmpty()) updateTab(pk, tab) { it.copy(posts = cached, loading = false) }
            }
            try {
                val (posts, next) = fetch(pk, tab, until = null)
                updateTab(pk, tab) { it.copy(posts = posts, nextUntil = next, loading = false, error = false, loaded = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateTab(pk, tab) { it.copy(loading = false, error = it.posts.isEmpty()) }
                if (_state.value.tabState(tab).posts.isNotEmpty()) _messages.tryEmit(e.toUiMessage())
            }
            update(pk) { it.copy(refreshing = false) }
        }
    }

    fun loadMore() {
        val s = _state.value
        val pk = s.pubkey ?: return
        val tab = s.tab
        val ts = s.current
        val until = ts.nextUntil ?: return
        if (ts.loading || ts.loadingMore) return
        updateTab(pk, tab) { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val (posts, next) = fetch(pk, tab, until)
                updateTab(pk, tab) { t ->
                    val seen = t.posts.mapTo(HashSet()) { it.id }
                    t.copy(posts = t.posts + posts.filter { seen.add(it.id) }, nextUntil = next, loadingMore = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                updateTab(pk, tab) { it.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun fetch(pk: String, tab: ProfileTab, until: Long?): Pair<List<PicturePost>, Long?> = when (tab) {
        ProfileTab.Posts -> feed.userPosts(pk, until).let { it.posts to it.nextUntil }
        ProfileTab.Tagged -> feed.taggedPosts(pk, until).let { it.posts to it.nextUntil }
        ProfileTab.Reposts -> {
            val items = feed.reposts(pk, until)
            val next = if (items.isEmpty()) null else items.minOf { it.createdAt } - 1
            items.map { it.post } to next
        }
    }

    fun toggleFollow() {
        val s = _state.value
        val pk = s.pubkey ?: return
        if (s.isOwn || s.followBusy) return
        if (accountManager.signerOrNull() == null) {
            accountManager.requestLogin()
            return
        }
        val follow = !s.isFollowing
        update(pk) { it.copy(followBusy = true) }
        viewModelScope.launch {
            try {
                if (follow) contacts.follow(pk) else contacts.unfollow(pk)
                update(pk) { it.copy(followers = it.followers?.let { n -> (n + if (follow) 1 else -1).coerceAtLeast(0) }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.toUiMessage())
            }
            update(pk) { it.copy(followBusy = false) }
        }
    }

    /** True when the user may message (logged in); otherwise asks for login. */
    fun canMessage(): Boolean {
        if (accountManager.signerOrNull() != null) return true
        accountManager.requestLogin()
        return false
    }

    fun requestLogin() {
        accountManager.requestLogin()
    }

    private inline fun update(pk: String, crossinline block: (ProfileUiState) -> ProfileUiState) {
        _state.update { if (it.pubkey == pk) block(it) else it }
    }

    private inline fun updateTab(pk: String, tab: ProfileTab, crossinline block: (TabState) -> TabState) {
        update(pk) { s ->
            when (tab) {
                ProfileTab.Posts -> s.copy(posts = block(s.posts))
                ProfileTab.Reposts -> s.copy(reposts = block(s.reposts))
                ProfileTab.Tagged -> s.copy(tagged = block(s.tagged))
            }
        }
    }

    private companion object {
        const val FOLLOWER_LIMIT = 500
    }
}
