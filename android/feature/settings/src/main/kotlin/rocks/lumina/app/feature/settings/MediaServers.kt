package rocks.lumina.app.feature.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
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
import rocks.lumina.app.core.data.Publisher
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaSwitch
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.media.BlossomClient
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

/** Result of a reachability test. */
sealed interface ServerTest {
    data object Running : ServerTest
    data class Ok(val millis: Long) : ServerTest
    data object Unreachable : ServerTest
}

data class MediaServersUiState(
    val loggedIn: Boolean = true,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val servers: List<String> = emptyList(),
    val savedServers: List<String> = emptyList(),
    val useDefaults: Boolean = true,
    val input: String = "",
    val inputError: AddError? = null,
    val tests: Map<String, ServerTest> = emptyMap(),
    val saving: Boolean = false,
) {
    val dirty: Boolean get() = !ListEditing.sameUrls(servers, savedServers)
}

@HiltViewModel
class MediaServersViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val relayRepository: RelayRepository,
    private val publisher: Publisher,
    private val blossom: BlossomClient,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaServersUiState(loggedIn = accountManager.activePubkey != null))
    val state: StateFlow<MediaServersUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            relayRepository.blossomServers.collect { list ->
                _state.update { s -> s.copy(savedServers = list, servers = if (s.dirty) s.servers else list) }
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(useDefaults = s.useDefaultBlossomServers) } }
        }
        refresh()
    }

    fun refresh() {
        val pk = accountManager.activePubkey ?: return
        _state.update { it.copy(loading = relayRepository.blossomListEvent == null && relayRepository.relayListEvent == null, loadFailed = false) }
        viewModelScope.launch {
            try {
                relayRepository.loadOwn(pk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadFailed = relayRepository.blossomListEvent == null && relayRepository.relayListEvent == null) }
                _messages.trySend(UiMessage.Res(R.string.settings_load_failed))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun setInput(value: String) = _state.update { it.copy(input = value, inputError = null) }

    fun add() = _state.update { s ->
        val result = ListEditing.addServer(s.servers, s.input)
        if (result.error != null) s.copy(inputError = result.error) else s.copy(servers = result.list, input = "", inputError = null)
    }

    fun remove(url: String) = _state.update { s -> s.copy(servers = s.servers.filterNot { it == url }) }

    fun move(index: Int, delta: Int) = _state.update { s -> s.copy(servers = ListEditing.move(s.servers, index, delta)) }

    fun discard() = _state.update { s -> s.copy(servers = s.savedServers, input = "", inputError = null) }

    fun setUseDefaults(value: Boolean) = settings.setUseDefaultBlossom(value)

    fun test(url: String) {
        if (_state.value.tests[url] == ServerTest.Running) return
        _state.update { it.copy(tests = it.tests + (url to ServerTest.Running)) }
        viewModelScope.launch {
            val ms = runCatching { blossom.test(url) }.getOrNull()
            _state.update { it.copy(tests = it.tests + (url to (if (ms != null) ServerTest.Ok(ms) else ServerTest.Unreachable))) }
        }
    }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                val event = publisher.signAndPublish(
                    Kinds.BLOSSOM_SERVERS,
                    "",
                    s.servers.map { arrayOf("server", it) },
                    extraRelays = RelayDefaults.indexerRelays,
                    notifyTagged = false,
                )
                relayRepository.applyOwn(event)
                _state.update { it.copy(savedServers = s.servers) }
                _messages.trySend(UiMessage.Res(R.string.settings_saved))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend(e.toUiMessage())
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

class MediaServersActions(
    val onBack: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val setInput: (String) -> Unit = {},
    val add: () -> Unit = {},
    val remove: (String) -> Unit = {},
    val move: (index: Int, delta: Int) -> Unit = { _, _ -> },
    val test: (String) -> Unit = {},
    val setUseDefaults: (Boolean) -> Unit = {},
    val discard: () -> Unit = {},
    val save: () -> Unit = {},
)

@Composable
fun MediaServersRoute(onBack: () -> Unit) {
    val viewModel: MediaServersViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessageEffect(viewModel.messages)
    MediaServersScreen(
        state,
        MediaServersActions(
            onBack = onBack,
            onLogin = viewModel::requestLogin,
            onRetry = viewModel::refresh,
            setInput = viewModel::setInput,
            add = viewModel::add,
            remove = viewModel::remove,
            move = viewModel::move,
            test = viewModel::test,
            setUseDefaults = viewModel::setUseDefaults,
            discard = viewModel::discard,
            save = viewModel::save,
        ),
    )
}

@Composable
fun MediaServersScreen(state: MediaServersUiState, actions: MediaServersActions) {
    val inputError = when (state.inputError) {
        AddError.INVALID -> stringResource(R.string.settings_media_invalid)
        AddError.DUPLICATE -> stringResource(R.string.settings_media_duplicate)
        null -> null
    }
    SettingsScaffold(
        title = stringResource(R.string.settings_media),
        onBack = actions.onBack,
        bottomBar = if (state.loggedIn && state.dirty) {
            { UnsavedBar(state.saving, true, actions.discard, actions.save) }
        } else {
            null
        },
    ) {
        when {
            !state.loggedIn -> item { LoginRequired(actions.onLogin) }
            state.loading -> item { SettingsListSkeleton(rows = 3) }
            state.loadFailed -> item { ErrorState(message = stringResource(R.string.settings_load_failed), onRetry = actions.onRetry) }
            else -> {
                item { SectionHeader(stringResource(R.string.settings_media)) }
                item { HelpText(stringResource(R.string.settings_media_help)) }
                if (state.servers.isEmpty()) item { HelpText(stringResource(R.string.settings_media_empty)) }
                items(state.servers.size, key = { "s" + state.servers[it] }) { i ->
                    val url = state.servers[i]
                    ServerRow(
                        url = url,
                        test = state.tests[url],
                        enabled = !state.saving,
                        canMoveUp = i > 0,
                        canMoveDown = i < state.servers.lastIndex,
                        onTest = { actions.test(url) },
                        onUp = { actions.move(i, -1) },
                        onDown = { actions.move(i, 1) },
                        onRemove = { actions.remove(url) },
                    )
                }
                item {
                    AddField(
                        value = state.input,
                        onValueChange = actions.setInput,
                        label = stringResource(R.string.settings_media_add_label),
                        placeholder = stringResource(R.string.settings_media_placeholder),
                        onAdd = actions.add,
                        error = inputError,
                        enabled = !state.saving,
                    )
                }
                item {
                    SettingsRow(
                        title = stringResource(R.string.settings_media_defaults),
                        subtitle = stringResource(R.string.settings_media_defaults_sub),
                        onClick = { actions.setUseDefaults(!state.useDefaults) },
                        trailing = { LuminaSwitch(state.useDefaults, null) },
                    )
                }
                if (state.useDefaults || state.servers.isEmpty()) {
                    item { SectionHeader(stringResource(R.string.settings_media_default_list)) }
                    items(RelayDefaults.blossomServers.size, key = { "def" + RelayDefaults.blossomServers[it] }) { i ->
                        val url = RelayDefaults.blossomServers[i]
                        UrlRow(url, LucideGlyphs.Server, subtitle = { TestLabel(state.tests[url]) }) {
                            TestButton(url, state.tests[url]) { actions.test(url) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerRow(
    url: String,
    test: ServerTest?,
    enabled: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onTest: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    val label = url.removePrefix("https://").removeSuffix("/")
    UrlRow(url, LucideGlyphs.Server, subtitle = { TestLabel(test) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TestButton(url, test, onTest)
            LuminaIconButton(LucideGlyphs.ArrowUp, stringResource(R.string.settings_move_up, label), onUp, enabled = enabled && canMoveUp, iconSize = 20.dp)
            LuminaIconButton(LucideGlyphs.ArrowDown, stringResource(R.string.settings_move_down, label), onDown, enabled = enabled && canMoveDown, iconSize = 20.dp)
            RemoveButton(label, onRemove, enabled = enabled)
        }
    }
}

@Composable
private fun TestButton(url: String, test: ServerTest?, onTest: () -> Unit) {
    if (test == ServerTest.Running) {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.size(48.dp), contentAlignment = Alignment.Center) { Spinner(size = 18.dp) }
    } else {
        LuminaIconButton(
            LucideGlyphs.Plug,
            stringResource(R.string.settings_media_test_label, url.removePrefix("https://").removeSuffix("/")),
            onTest,
            iconSize = 20.dp,
            tint = LuminaTheme.colors.mutedForeground,
        )
    }
}

@Composable
private fun TestLabel(test: ServerTest?) {
    val (text, color) = when (test) {
        null -> return
        ServerTest.Running -> stringResource(R.string.settings_media_testing) to LuminaTheme.colors.mutedForeground
        is ServerTest.Ok -> stringResource(R.string.settings_media_ok, test.millis.toInt()) to LuminaTheme.colors.mutedForeground
        ServerTest.Unreachable -> stringResource(R.string.settings_media_unreachable) to LuminaTheme.colors.destructive
    }
    Text(text, style = LuminaTheme.typography.caption, color = color)
}

private val mediaPreview = MediaServersUiState(
    servers = listOf("https://blossom.band/", "https://cdn.satellite.earth/"),
    savedServers = listOf("https://blossom.band/"),
    tests = mapOf("https://blossom.band/" to ServerTest.Ok(142), "https://cdn.satellite.earth/" to ServerTest.Unreachable),
)

@Preview(name = "Media – light")
@Composable
private fun MediaLightPreview() = LuminaTheme(dark = false) { MediaServersScreen(mediaPreview, MediaServersActions()) }

@Preview(name = "Media – dark")
@Composable
private fun MediaDarkPreview() = LuminaTheme(dark = true) {
    MediaServersScreen(mediaPreview.copy(useDefaults = false, tests = mapOf("https://blossom.band/" to ServerTest.Running)), MediaServersActions())
}

@Preview(name = "Media empty – light")
@Composable
private fun MediaEmptyPreview() = LuminaTheme(dark = false) { MediaServersScreen(MediaServersUiState(), MediaServersActions()) }
