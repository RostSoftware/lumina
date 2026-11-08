package rocks.lumina.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.DmRepository
import rocks.lumina.app.core.data.SendState
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

data class ChatUiState(
    val peer: String,
    val loggedIn: Boolean = true,
    val loading: Boolean = true,
    val items: List<ChatItem> = emptyList(),
    /** Id of the newest message, used to auto-scroll when something new arrives. */
    val newestId: String? = null,
) {
    val showSkeleton: Boolean get() = loggedIn && loading && items.isEmpty()
}

@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
class ChatViewModel @AssistedInject constructor(
    @Assisted rawPeer: String,
    private val dms: DmRepository,
    private val accountManager: AccountManager,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(peer: String): ChatViewModel
    }

    /** Accepts hex pubkeys as well as npub/nprofile inputs. */
    val peer: String = NostrLinks.pubkeyOf(rawPeer) ?: rawPeer.lowercase()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    val state: StateFlow<ChatUiState> = combine(
        accountManager.active,
        dms.loading,
        dms.messages(peer),
    ) { active, loading, list ->
        val items = if (active != null) ChatItems.build(list, active.pubkey) else emptyList()
        ChatUiState(
            peer = peer,
            loggedIn = active != null,
            loading = loading,
            items = items,
            newestId = list.maxByOrNull { it.createdAt }?.id,
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState(peer, loggedIn = accountManager.activePubkey != null))

    init {
        viewModelScope.launch {
            accountManager.active.map { it?.pubkey }.distinctUntilChanged().collect { if (it != null) dms.start() }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun onDraftChange(text: String) {
        _draft.value = text
    }

    fun send() {
        val text = _draft.value.trim()
        if (text.isEmpty()) return
        if (accountManager.activePubkey == null) {
            accountManager.requestLogin()
            return
        }
        _draft.value = ""
        viewModelScope.launch {
            runCatching { dms.send(peer, text) }.onFailure { _messages.send(it.toUiMessage()) }
        }
    }

    fun retry(localId: String) {
        val message = (state.value.items.firstOrNull { it is ChatItem.Bubble && it.message.id == localId } as? ChatItem.Bubble)?.message
        if (message == null || message.state != SendState.FAILED) return
        viewModelScope.launch {
            runCatching { dms.retry(peer, localId) }.onFailure { _messages.send(it.toUiMessage()) }
        }
    }

    /** Called while the chat is visible and whenever a new message arrives. */
    fun markRead() {
        if (accountManager.activePubkey == null) return
        viewModelScope.launch { runCatching { dms.markRead(peer) } }
    }
}
