package rocks.lumina.app.feature.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.ListsRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaSwitch
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.model.MuteList
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

data class ContentUiState(
    val loggedIn: Boolean = true,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val mutes: MuteList = MuteList(),
    val showSensitive: Boolean = false,
    val systemNotifications: Boolean = true,
    val wordInput: String = "",
    val tagInput: String = "",
    /** Mute items with a pending publish ("p:<hex>", "word:<w>", "t:<tag>"). */
    val busy: Set<String> = emptySet(),
)

@HiltViewModel
class ContentViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val lists: ListsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ContentUiState(loggedIn = accountManager.activePubkey != null))
    val state: StateFlow<ContentUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { lists.mutes.collect { m -> _state.update { it.copy(mutes = m) } } }
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(showSensitive = s.showSensitive, systemNotifications = s.systemNotifications) } }
        }
        refresh()
    }

    fun refresh() {
        val pk = accountManager.activePubkey ?: return
        _state.update { it.copy(loading = lists.mutes.value.isEmpty(), loadFailed = false) }
        viewModelScope.launch {
            try {
                lists.loadOwn(pk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadFailed = lists.mutes.value.isEmpty()) }
                _messages.trySend(UiMessage.Res(R.string.settings_load_failed))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun setShowSensitive(value: Boolean) = settings.setShowSensitive(value)

    fun setSystemNotifications(value: Boolean) = settings.setSystemNotifications(value)

    fun setWordInput(value: String) = _state.update { it.copy(wordInput = value) }

    fun setTagInput(value: String) = _state.update { it.copy(tagInput = value) }

    fun unmuteUser(pubkey: String) = mutate("p:$pubkey") { lists.unmutePubkey(pubkey) }

    fun unmuteWord(word: String) = mutate("word:$word") { lists.unmuteWord(word) }

    fun unmuteTag(tag: String) = mutate("t:$tag") { lists.unmuteHashtag(tag) }

    fun addWord() {
        val word = _state.value.wordInput.trim()
        if (word.isEmpty()) return
        _state.update { it.copy(wordInput = "") }
        mutate("word:${word.lowercase()}") { lists.muteWord(word) }
    }

    fun addTag() {
        val tag = _state.value.tagInput.trim().removePrefix("#").trim()
        if (tag.isEmpty() || tag.any { it.isWhitespace() }) return
        _state.update { it.copy(tagInput = "") }
        mutate("t:${tag.lowercase()}") { lists.muteHashtag(tag) }
    }

    private fun mutate(key: String, block: suspend () -> Unit) {
        if (key in _state.value.busy) return
        _state.update { it.copy(busy = it.busy + key) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend(e.toUiMessage())
            } finally {
                _state.update { it.copy(busy = it.busy - key) }
            }
        }
    }
}

class ContentActions(
    val onBack: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenProfile: (String) -> Unit = {},
    val setShowSensitive: (Boolean) -> Unit = {},
    val setSystemNotifications: (Boolean) -> Unit = {},
    val unmuteUser: (String) -> Unit = {},
    val unmuteWord: (String) -> Unit = {},
    val unmuteTag: (String) -> Unit = {},
    val setWordInput: (String) -> Unit = {},
    val addWord: () -> Unit = {},
    val setTagInput: (String) -> Unit = {},
    val addTag: () -> Unit = {},
)

@Composable
fun ContentRoute(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val viewModel: ContentViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessageEffect(viewModel.messages)
    ContentScreen(
        state,
        ContentActions(
            onBack = onBack,
            onLogin = viewModel::requestLogin,
            onRetry = viewModel::refresh,
            onOpenProfile = onOpenProfile,
            setShowSensitive = viewModel::setShowSensitive,
            setSystemNotifications = viewModel::setSystemNotifications,
            unmuteUser = viewModel::unmuteUser,
            unmuteWord = viewModel::unmuteWord,
            unmuteTag = viewModel::unmuteTag,
            setWordInput = viewModel::setWordInput,
            addWord = viewModel::addWord,
            setTagInput = viewModel::setTagInput,
            addTag = viewModel::addTag,
        ),
    )
}

@Composable
fun ContentScreen(state: ContentUiState, actions: ContentActions) {
    SettingsScaffold(stringResource(R.string.settings_content), actions.onBack) {
        if (!state.loggedIn) {
            item { LoginRequired(actions.onLogin) }
            return@SettingsScaffold
        }
        item { SectionHeader(stringResource(R.string.settings_section_display)) }
        item {
            SettingsRow(
                stringResource(R.string.settings_sensitive),
                subtitle = stringResource(R.string.settings_sensitive_sub),
                glyph = LucideGlyphs.Eye,
                onClick = { actions.setShowSensitive(!state.showSensitive) },
                trailing = { LuminaSwitch(state.showSensitive, null) },
            )
        }
        item {
            SettingsRow(
                stringResource(R.string.settings_notifications),
                subtitle = stringResource(R.string.settings_notifications_sub),
                glyph = LucideGlyphs.Bell,
                onClick = { actions.setSystemNotifications(!state.systemNotifications) },
                trailing = { LuminaSwitch(state.systemNotifications, null) },
            )
        }

        when {
            state.loading -> item { SettingsListSkeleton(rows = 3, avatars = true) }
            state.loadFailed -> item { ErrorState(message = stringResource(R.string.settings_load_failed), onRetry = actions.onRetry) }
            else -> {
                item { SectionHeader(stringResource(R.string.settings_muted_users)) }
                val users = state.mutes.pubkeys.sorted()
                if (users.isEmpty()) item { HelpText(stringResource(R.string.settings_muted_users_empty)) }
                items(users.size, key = { "p" + users[it] }) { i ->
                    val pk = users[i]
                    UserRow(pk, onClick = { actions.onOpenProfile(pk) }) {
                        if ("p:$pk" in state.busy) {
                            Spinner(Modifier.size(20.dp), size = 20.dp)
                        } else {
                            LuminaButton(stringResource(R.string.settings_unmute), { actions.unmuteUser(pk) }, style = ButtonStyle.Secondary)
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.settings_muted_words)) }
                val words = state.mutes.words.sorted()
                if (words.isEmpty()) item { HelpText(stringResource(R.string.settings_muted_words_empty)) }
                items(words.size, key = { "w" + words[it] }) { i ->
                    val word = words[i]
                    MuteItemRow(word, LucideGlyphs.VolumeX, busy = "word:$word" in state.busy) { actions.unmuteWord(word) }
                }
                item {
                    AddField(
                        value = state.wordInput,
                        onValueChange = actions.setWordInput,
                        label = stringResource(R.string.settings_mute_word_label),
                        onAdd = actions.addWord,
                        keyboardType = KeyboardType.Text,
                        glyph = LucideGlyphs.VolumeX,
                    )
                }

                item { SectionHeader(stringResource(R.string.settings_muted_tags)) }
                val tags = state.mutes.hashtags.sorted()
                if (tags.isEmpty()) item { HelpText(stringResource(R.string.settings_muted_tags_empty)) }
                items(tags.size, key = { "t" + tags[it] }) { i ->
                    val tag = tags[i]
                    MuteItemRow("#$tag", LucideGlyphs.Hash, busy = "t:$tag" in state.busy) { actions.unmuteTag(tag) }
                }
                item {
                    AddField(
                        value = state.tagInput,
                        onValueChange = actions.setTagInput,
                        label = stringResource(R.string.settings_mute_tag_label),
                        onAdd = actions.addTag,
                        keyboardType = KeyboardType.Text,
                        glyph = LucideGlyphs.Hash,
                    )
                }
            }
        }
    }
}

@Composable
private fun MuteItemRow(text: String, glyph: rocks.lumina.app.core.designsystem.icon.LucideGlyph, busy: Boolean, onRemove: () -> Unit) {
    UrlRow(text, glyph) {
        if (busy) {
            androidx.compose.foundation.layout.Box(Modifier.size(48.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { Spinner(size = 18.dp) }
        } else {
            RemoveButton(text, onRemove)
        }
    }
}

private val contentPreview = ContentUiState(
    mutes = MuteList(pubkeys = setOf(PREVIEW_PK, PREVIEW_PK2), words = setOf("spoiler", "crypto"), hashtags = setOf("nsfw")),
    showSensitive = false,
    busy = setOf("word:crypto"),
)

@Preview(name = "Content – light")
@Composable
private fun ContentLightPreview() = LuminaTheme(dark = false) { ContentScreen(contentPreview, ContentActions()) }

@Preview(name = "Content – dark")
@Composable
private fun ContentDarkPreview() = LuminaTheme(dark = true) { ContentScreen(contentPreview.copy(showSensitive = true), ContentActions()) }

@Preview(name = "Content empty – light")
@Composable
private fun ContentEmptyPreview() = LuminaTheme(dark = false) { ContentScreen(ContentUiState(), ContentActions()) }

@Preview(name = "Content loading – dark")
@Composable
private fun ContentLoadingPreview() = LuminaTheme(dark = true) { ContentScreen(ContentUiState(loading = true), ContentActions()) }
