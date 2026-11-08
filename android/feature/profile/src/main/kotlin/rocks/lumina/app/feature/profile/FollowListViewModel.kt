package rocks.lumina.app.feature.profile

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
import rocks.lumina.app.core.data.ContactRepository
import rocks.lumina.app.core.data.ListsRepository
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

sealed interface FollowListContent {
    data object Loading : FollowListContent
    data object Error : FollowListContent
    data class Loaded(val pubkeys: List<String>, val capped: Boolean) : FollowListContent
}

data class FollowListUiState(
    val followers: Boolean = true,
    val content: FollowListContent = FollowListContent.Loading,
    val myPubkey: String? = null,
    val following: Set<String> = emptySet(),
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val contacts: ContactRepository,
    private val lists: ListsRepository,
    private val accountManager: AccountManager,
) : ViewModel() {
    private val _state = MutableStateFlow(FollowListUiState())
    val state: StateFlow<FollowListUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var target: Pair<String, Boolean>? = null

    init {
        viewModelScope.launch {
            contacts.following.collect { f -> _state.update { it.copy(following = f, myPubkey = accountManager.activePubkey) } }
        }
    }

    fun bind(pubkey: String, followers: Boolean) {
        if (target == pubkey to followers) return
        target = pubkey to followers
        _state.update { it.copy(followers = followers) }
        load()
    }

    fun load() {
        val (pk, followers) = target ?: return
        _state.update { it.copy(content = FollowListContent.Loading, myPubkey = accountManager.activePubkey) }
        viewModelScope.launch {
            val result = try {
                val list = if (followers) contacts.followersOf(pk, LIMIT) else contacts.followingOf(pk)
                val mutes = lists.mutes.value.pubkeys
                FollowListContent.Loaded(list.filterNot { it in mutes }, capped = followers && list.size >= LIMIT)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                FollowListContent.Error
            }
            if (target == pk to followers) _state.update { it.copy(content = result) }
        }
    }

    fun toggleFollow(pubkey: String) {
        if (accountManager.signerOrNull() == null) {
            accountManager.requestLogin()
            return
        }
        val s = _state.value
        if (pubkey == s.myPubkey || pubkey in s.busy) return
        val follow = pubkey !in s.following
        _state.update { it.copy(busy = it.busy + pubkey) }
        viewModelScope.launch {
            try {
                if (follow) contacts.follow(pubkey) else contacts.unfollow(pubkey)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit(e.toUiMessage())
            }
            _state.update { it.copy(busy = it.busy - pubkey) }
        }
    }

    private companion object {
        const val LIMIT = 500
    }
}
