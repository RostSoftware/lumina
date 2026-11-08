package rocks.lumina.app.feature.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.FeedRepository
import rocks.lumina.app.core.data.InteractionRepository
import rocks.lumina.app.core.data.PostRepository
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.nostr.model.Comment
import rocks.lumina.app.core.nostr.model.CommentParser
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

data class PostDetailUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val post: PicturePost? = null,
    val commentsLoading: Boolean = true,
    val commentsError: Boolean = false,
    val threads: List<CommentThread> = emptyList(),
    val replyingTo: Comment? = null,
    val sending: Boolean = false,
    val suggestions: List<Profile> = emptyList(),
    val loggedIn: Boolean = false,
    val myPubkey: String? = null,
)

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val feed: FeedRepository,
    private val posts: PostRepository,
    private val interactions: InteractionRepository,
    private val profiles: ProfileRepository,
    private val accountManager: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _commented = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Emits the post id after a comment was published (to bump counts). */
    val commented: SharedFlow<String> = _commented.asSharedFlow()

    private var loadedId: String? = null
    private var searchJob: Job? = null
    private var comments: List<Comment> = emptyList()

    init {
        viewModelScope.launch {
            accountManager.active.collect { a -> _state.update { it.copy(loggedIn = a != null, myPubkey = a?.pubkey) } }
        }
    }

    fun load(postId: String, relays: List<String>, author: String?) {
        if (loadedId == postId) return
        loadedId = postId
        viewModelScope.launch {
            val cached = feed.cachedPost(postId)
            if (cached != null) onPost(cached)
            val fresh = runCatching { feed.post(postId, relays, author) }.getOrNull()
            when {
                fresh != null -> if (cached == null) onPost(fresh)
                cached == null -> _state.update { it.copy(loading = false, notFound = true) }
            }
        }
    }

    private fun onPost(post: PicturePost) {
        _state.update { it.copy(loading = false, notFound = false, post = post) }
        loadComments(post)
    }

    private fun loadComments(post: PicturePost) {
        viewModelScope.launch {
            val cached = runCatching { posts.cachedComments(post) }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) setComments(cached)
            runCatching { posts.comments(post) }
                .onSuccess { setComments(it) }
                .onFailure { _state.update { s -> s.copy(commentsLoading = false, commentsError = s.threads.isEmpty()) } }
        }
    }

    private fun setComments(list: List<Comment>) {
        comments = list
        profiles.prefetch(list.map { it.pubkey })
        _state.update { it.copy(threads = buildThreads(list), commentsLoading = false, commentsError = false) }
    }

    fun retryComments() {
        val post = _state.value.post ?: return
        _state.update { it.copy(commentsLoading = true, commentsError = false) }
        loadComments(post)
    }

    fun replyTo(comment: Comment?) {
        _state.update { it.copy(replyingTo = comment) }
    }

    /** Called on every composer change for @mention autocomplete. */
    fun onComposerChanged(text: String, cursor: Int) {
        val query = mentionQueryAt(text, cursor)
        searchJob?.cancel()
        if (query == null) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            val results = runCatching { profiles.search(query, limit = 6) }.getOrDefault(emptyList())
            _state.update { it.copy(suggestions = results) }
        }
    }

    fun mentionFor(profile: Profile): String = "nostr:" + NostrLinks.npub(profile.pubkey)

    /** Publishes a NIP-22 comment. Returns true on success (the composer is then cleared). */
    fun send(text: String, onSent: () -> Unit) {
        val post = _state.value.post ?: return
        if (text.isBlank() || _state.value.sending) return
        if (accountManager.signerOrNull() == null) {
            accountManager.requestLogin()
            return
        }
        val parent = _state.value.replyingTo
        _state.update { it.copy(sending = true) }
        viewModelScope.launch {
            runCatching { interactions.comment(post, text.trim(), parent) }
                .onSuccess { event ->
                    CommentParser.parse(event)?.let { setComments(comments + it) }
                    _state.update { it.copy(sending = false, replyingTo = null, suggestions = emptyList()) }
                    _commented.tryEmit(post.id)
                    onSent()
                }
                .onFailure { e ->
                    _state.update { it.copy(sending = false) }
                    _messages.tryEmit(e.toUiMessage())
                }
        }
    }

    fun deleteComment(comment: Comment) {
        viewModelScope.launch {
            val before = comments
            setComments(comments.filterNot { it.id == comment.id })
            runCatching { interactions.deleteComment(comment) }
                .onSuccess { _messages.tryEmit(UiMessage.Res(R.string.post_comment_deleted)) }
                .onFailure { e ->
                    setComments(before)
                    _messages.tryEmit(e.toUiMessage())
                }
        }
    }
}
