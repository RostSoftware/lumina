package rocks.lumina.app.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.Conversation
import rocks.lumina.app.core.data.DmRepository
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager

data class ConversationsUiState(
    val loggedIn: Boolean = true,
    val me: String? = null,
    val loading: Boolean = true,
    val conversations: List<Conversation> = emptyList(),
) {
    val showSkeleton: Boolean get() = loggedIn && loading && conversations.isEmpty()
    val isEmpty: Boolean get() = loggedIn && !loading && conversations.isEmpty()
}

data class UserSearchState(
    val open: Boolean = false,
    val query: String = "",
    val searching: Boolean = false,
    val results: List<Profile> = emptyList(),
    val failed: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val dms: DmRepository,
    private val accountManager: AccountManager,
    private val profiles: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<ConversationsUiState> = combine(
        accountManager.active,
        dms.loading,
        dms.conversations,
    ) { active, loading, conversations ->
        ConversationsUiState(
            loggedIn = active != null,
            me = active?.pubkey,
            loading = loading,
            conversations = if (active != null) conversations else emptyList(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationsUiState(loggedIn = accountManager.activePubkey != null))

    private val _search = MutableStateFlow(UserSearchState())
    val search: StateFlow<UserSearchState> = _search.asStateFlow()

    init {
        viewModelScope.launch {
            accountManager.active.map { it?.pubkey }.distinctUntilChanged().collect { pubkey ->
                if (pubkey != null) dms.start()
            }
        }
        viewModelScope.launch {
            dms.conversations.collect { list -> profiles.prefetch(list.map { it.peer }) }
        }
        viewModelScope.launch {
            _search.map { it.query.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS).collect { query ->
                runSearch(query)
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun openSearch() = _search.update { UserSearchState(open = true) }

    fun closeSearch() = _search.update { UserSearchState(open = false) }

    fun onQueryChange(query: String) = _search.update { it.copy(query = query) }

    private suspend fun runSearch(query: String) {
        if (query.isEmpty()) {
            _search.update { it.copy(searching = false, results = emptyList(), failed = false) }
            return
        }
        _search.update { it.copy(searching = true, failed = false) }
        val result = runCatching { profiles.search(query) }
        _search.update { current ->
            if (current.query.trim() != query) return@update current
            current.copy(
                searching = false,
                results = result.getOrDefault(emptyList()).filter { it.pubkey != accountManager.activePubkey },
                failed = result.isFailure,
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
