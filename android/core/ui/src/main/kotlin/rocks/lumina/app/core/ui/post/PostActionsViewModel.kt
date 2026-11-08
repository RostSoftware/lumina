package rocks.lumina.app.core.ui.post

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.AppSettings
import rocks.lumina.app.core.data.Engagement
import rocks.lumina.app.core.data.InteractionRepository
import rocks.lumina.app.core.data.ListsRepository
import rocks.lumina.app.core.data.PostRepository
import rocks.lumina.app.core.data.PostStats
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.data.ReportType
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.nostr.model.MuteList
import rocks.lumina.app.core.nostr.model.PicturePost
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.formatSats
import rocks.lumina.app.core.ui.util.toUiMessage
import rocks.lumina.app.core.wallet.LnurlException
import rocks.lumina.app.core.wallet.ZapResult
import rocks.lumina.app.core.wallet.ZapService

/** Which post sheet is open. */
public sealed interface PostSheet {
    public data class Menu(val post: PicturePost) : PostSheet
    public data class Zap(val pubkey: String, val post: PicturePost?) : PostSheet
    public data class Report(val pubkey: String, val post: PicturePost?) : PostSheet
    public data class Raw(val post: PicturePost) : PostSheet
    public data class EngagementList(val post: PicturePost, val data: Engagement?) : PostSheet
}

/**
 * Shared post interactions for every screen that shows posts (feed, discover, profile, detail):
 * stats loading, optimistic like/repost/bookmark with rollback, zaps, delete, report, mute.
 */
@HiltViewModel
public class PostActionsViewModel @Inject constructor(
    private val posts: PostRepository,
    private val interactions: InteractionRepository,
    private val lists: ListsRepository,
    private val zaps: ZapService,
    private val relays: RelayRepository,
    private val accountManager: AccountManager,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val _stats = MutableStateFlow<Map<String, PostStats>>(emptyMap())
    public val stats: StateFlow<Map<String, PostStats>> = _stats.asStateFlow()

    public val bookmarks: StateFlow<Set<String>> = lists.bookmarks.map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    public val mutes: StateFlow<MuteList> = lists.mutes
    public val settings: StateFlow<AppSettings> = settingsRepository.settings

    private val _removed = MutableStateFlow<Set<String>>(emptySet())
    /** Posts deleted in this session (hidden immediately). */
    public val removed: StateFlow<Set<String>> = _removed.asStateFlow()

    private val _sheet = MutableStateFlow<PostSheet?>(null)
    public val sheet: StateFlow<PostSheet?> = _sheet.asStateFlow()

    private val _busy = MutableStateFlow(false)
    public val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    public val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private val _externalInvoices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** bolt11 invoices to open in an external Lightning wallet (`lightning:` intent). */
    public val externalInvoices: SharedFlow<String> = _externalInvoices.asSharedFlow()

    private val loading = HashSet<String>()

    public val myPubkey: String? get() = accountManager.activePubkey

    public fun relayHint(): String? = relays.writeRelays().firstOrNull()

    /** Loads stats for posts not loaded yet (cache first, then relays). */
    public fun loadStats(list: List<PicturePost>, force: Boolean = false) {
        val todo = list.filter { force || (it.id !in _stats.value && it.id !in loading) }
        if (todo.isEmpty()) return
        loading.addAll(todo.map { it.id })
        viewModelScope.launch {
            runCatching { posts.cachedStats(todo) }.getOrNull()?.let { cached ->
                _stats.update { current -> current + cached.filterKeys { it !in current || force } }
            }
            runCatching { posts.stats(todo) }.getOrNull()?.let { fresh -> _stats.update { it + fresh } }
            loading.removeAll(todo.map { it.id }.toSet())
        }
    }

    private fun mutate(id: String, block: (PostStats) -> PostStats) {
        _stats.update { it + (id to block(it[id] ?: PostStats())) }
    }

    private fun requireLogin(): Boolean {
        if (accountManager.signerOrNull() == null) {
            accountManager.requestLogin()
            return false
        }
        return true
    }

    public fun toggleLike(post: PicturePost) {
        if (!requireLogin()) return
        val before = _stats.value[post.id] ?: PostStats()
        val reactionId = before.myReactionId
        if (reactionId != null) {
            mutate(post.id) { it.copy(likes = (it.likes - 1).coerceAtLeast(0), myReactionId = null) }
            viewModelScope.launch {
                runCatching { interactions.unlike(reactionId) }.onFailure { e ->
                    mutate(post.id) { before }
                    _messages.tryEmit(e.toUiMessage())
                }
            }
        } else {
            like(post, before)
        }
    }

    /** Double-tap: only ever likes (never unlikes), like Instagram. */
    public fun doubleTapLike(post: PicturePost) {
        if (!requireLogin()) return
        val before = _stats.value[post.id] ?: PostStats()
        if (before.likedByMe) return
        like(post, before)
    }

    private fun like(post: PicturePost, before: PostStats) {
        mutate(post.id) { it.copy(likes = it.likes + 1, myReactionId = PENDING) }
        viewModelScope.launch {
            runCatching { interactions.like(post) }
                .onSuccess { event -> mutate(post.id) { it.copy(myReactionId = event.id) } }
                .onFailure { e ->
                    mutate(post.id) { before }
                    _messages.tryEmit(e.toUiMessage())
                }
        }
    }

    public fun toggleRepost(post: PicturePost) {
        if (!requireLogin()) return
        val before = _stats.value[post.id] ?: PostStats()
        val repostId = before.myRepostId
        if (repostId != null) {
            mutate(post.id) { it.copy(reposts = (it.reposts - 1).coerceAtLeast(0), myRepostId = null) }
            viewModelScope.launch {
                runCatching { interactions.unrepost(repostId) }.onFailure { e ->
                    mutate(post.id) { before }
                    _messages.tryEmit(e.toUiMessage())
                }
            }
        } else {
            mutate(post.id) { it.copy(reposts = it.reposts + 1, myRepostId = PENDING) }
            viewModelScope.launch {
                runCatching { interactions.repost(post) }
                    .onSuccess { event -> mutate(post.id) { it.copy(myRepostId = event.id) } }
                    .onFailure { e ->
                        mutate(post.id) { before }
                        _messages.tryEmit(e.toUiMessage())
                    }
            }
        }
    }

    public fun toggleBookmark(post: PicturePost) {
        if (!requireLogin()) return
        viewModelScope.launch {
            runCatching { lists.toggleBookmark(post.id) }
                .onSuccess { added -> _messages.tryEmit(UiMessage.Res(if (added) R.string.post_saved else R.string.post_unsaved)) }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
        }
    }

    public fun openMenu(post: PicturePost) { _sheet.value = PostSheet.Menu(post) }

    public fun openZap(pubkey: String, post: PicturePost?) {
        if (!requireLogin()) return
        _sheet.value = PostSheet.Zap(pubkey, post)
    }

    public fun openReport(pubkey: String, post: PicturePost?) {
        if (!requireLogin()) return
        _sheet.value = PostSheet.Report(pubkey, post)
    }

    public fun openRaw(post: PicturePost) { _sheet.value = PostSheet.Raw(post) }

    public fun openEngagement(post: PicturePost) {
        _sheet.value = PostSheet.EngagementList(post, null)
        viewModelScope.launch {
            val data = runCatching { posts.engagement(post) }.getOrNull() ?: Engagement(emptyList(), emptyList(), emptyList())
            _sheet.update { s -> if (s is PostSheet.EngagementList && s.post.id == post.id) s.copy(data = data) else s }
        }
    }

    public fun dismissSheet() { _sheet.value = null }

    public fun zap(pubkey: String, post: PicturePost?, sats: Long, comment: String) {
        if (!requireLogin()) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { zaps.zap(pubkey, post?.event, sats, comment) }
                .onSuccess { result ->
                    _sheet.value = null
                    when (result) {
                        ZapResult.Paid -> {
                            _messages.tryEmit(UiMessage.Res(R.string.zap_sent, listOf(formatSats(sats))))
                            if (post != null) mutate(post.id) { it.copy(zapSats = it.zapSats + sats, zapCount = it.zapCount + 1, zappedByMe = true) }
                        }
                        is ZapResult.ExternalInvoice -> {
                            _messages.tryEmit(UiMessage.Res(R.string.zap_no_wallet))
                            _externalInvoices.tryEmit(result.bolt11)
                        }
                    }
                }
                .onFailure { e ->
                    _messages.tryEmit(if (e is LnurlException && e.message?.contains("no lightning address") == true) UiMessage.Res(R.string.zap_no_address) else e.toUiMessage())
                }
            _busy.value = false
        }
    }

    public fun delete(post: PicturePost) {
        if (!requireLogin()) return
        _sheet.value = null
        _removed.update { it + post.id }
        viewModelScope.launch {
            runCatching { interactions.deletePost(post) }
                .onSuccess { _messages.tryEmit(UiMessage.Res(R.string.post_deleted)) }
                .onFailure { e ->
                    _removed.update { it - post.id }
                    _messages.tryEmit(e.toUiMessage())
                }
        }
    }

    public fun report(pubkey: String, post: PicturePost?, type: ReportType, details: String) {
        if (!requireLogin()) return
        _busy.value = true
        viewModelScope.launch {
            runCatching { interactions.report(pubkey, post?.id, type, details) }
                .onSuccess {
                    _sheet.value = null
                    _messages.tryEmit(UiMessage.Res(R.string.post_reported))
                }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
            _busy.value = false
        }
    }

    public fun toggleMute(pubkey: String, displayName: String) {
        if (!requireLogin()) return
        _sheet.value = null
        val muted = pubkey in lists.mutes.value.pubkeys
        viewModelScope.launch {
            runCatching { if (muted) lists.unmutePubkey(pubkey) else lists.mutePubkey(pubkey) }
                .onSuccess { if (!muted) _messages.tryEmit(UiMessage.Res(R.string.post_muted, listOf(displayName))) }
                .onFailure { _messages.tryEmit(it.toUiMessage()) }
        }
    }

    public fun emit(message: UiMessage) {
        _messages.tryEmit(message)
    }

    /** Optimistically bumps the comment count after posting a comment. */
    public fun onCommented(postId: String) {
        mutate(postId) { it.copy(comments = it.comments + 1) }
    }

    private companion object {
        const val PENDING = "pending"
    }
}
