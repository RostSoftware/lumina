package rocks.lumina.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import rocks.lumina.app.core.data.Publisher
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.designsystem.component.ErrorState
import rocks.lumina.app.core.designsystem.component.LuminaChip
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.model.RelayEntry
import rocks.lumina.app.core.nostr.model.RelayListParser
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

data class RelaysUiState(
    val loggedIn: Boolean = true,
    val loading: Boolean = false,
    val loadFailed: Boolean = false,
    val usingDefaults: Boolean = false,
    val relays: List<RelayEntry> = emptyList(),
    val dmRelays: List<String> = emptyList(),
    val savedRelays: List<RelayEntry> = emptyList(),
    val savedDmRelays: List<String> = emptyList(),
    val relayInput: String = "",
    val relayError: AddError? = null,
    val dmInput: String = "",
    val dmError: AddError? = null,
    val saving: Boolean = false,
) {
    val relaysDirty: Boolean get() = !ListEditing.sameRelays(relays, savedRelays)
    val dmDirty: Boolean get() = !ListEditing.sameUrls(dmRelays, savedDmRelays)
    val dirty: Boolean get() = relaysDirty || dmDirty
    val hasWriteRelay: Boolean get() = ListEditing.hasWriteRelay(relays)
}

@HiltViewModel
class RelaysViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val relayRepository: RelayRepository,
    private val publisher: Publisher,
) : ViewModel() {
    private val _state = MutableStateFlow(RelaysUiState(loggedIn = accountManager.activePubkey != null))
    val state: StateFlow<RelaysUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(relayRepository.userRelays, relayRepository.dmRelays, relayRepository.hasPublishedRelayList) { r, dm, published ->
                Triple(r, dm, published)
            }.collect { (relays, dm, published) ->
                _state.update { s ->
                    val keepDraft = s.dirty
                    s.copy(
                        usingDefaults = !published,
                        savedRelays = relays,
                        savedDmRelays = dm,
                        relays = if (keepDraft) s.relays else relays,
                        dmRelays = if (keepDraft) s.dmRelays else dm,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val pk = accountManager.activePubkey ?: return
        _state.update { it.copy(loading = relayRepository.relayListEvent == null, loadFailed = false) }
        viewModelScope.launch {
            try {
                relayRepository.loadOwn(pk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadFailed = relayRepository.relayListEvent == null) }
                _messages.trySend(UiMessage.Res(R.string.settings_load_failed))
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun setRelayInput(value: String) = _state.update { it.copy(relayInput = value, relayError = null) }

    fun setDmInput(value: String) = _state.update { it.copy(dmInput = value, dmError = null) }

    fun addRelay() = _state.update { s ->
        val result = ListEditing.addRelay(s.relays, s.relayInput, NostrNetwork::normalizeOrNull)
        if (result.error != null) s.copy(relayError = result.error) else s.copy(relays = result.list, relayInput = "", relayError = null)
    }

    fun removeRelay(url: String) = _state.update { s -> s.copy(relays = s.relays.filterNot { it.url == url }) }

    fun toggle(url: String, read: Boolean) = _state.update { s -> s.copy(relays = ListEditing.toggle(s.relays, url, read)) }

    fun addDmRelay() = _state.update { s ->
        val result = ListEditing.addDmRelay(s.dmRelays, s.dmInput, NostrNetwork::normalizeOrNull)
        if (result.error != null) s.copy(dmError = result.error) else s.copy(dmRelays = result.list, dmInput = "", dmError = null)
    }

    fun removeDmRelay(url: String) = _state.update { s -> s.copy(dmRelays = s.dmRelays.filterNot { it == url }) }

    fun discard() = _state.update { s -> s.copy(relays = s.savedRelays, dmRelays = s.savedDmRelays, relayInput = "", dmInput = "", relayError = null, dmError = null) }

    fun save() {
        val s = _state.value
        if (s.saving || !s.dirty) return
        if (!s.hasWriteRelay) {
            _messages.trySend(UiMessage.Res(R.string.settings_relays_need_write))
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (s.relaysDirty) {
                    val event = publisher.signAndPublish(
                        Kinds.RELAY_LIST,
                        "",
                        RelayListParser.nip65Tags(s.relays).toList(),
                        extraRelays = (RelayDefaults.indexerRelays + s.relays.filter { it.write }.map { it.url }).distinct(),
                        notifyTagged = false,
                    )
                    relayRepository.applyOwn(event)
                }
                if (s.dmDirty) {
                    val event = publisher.signAndPublish(
                        Kinds.DM_RELAYS,
                        "",
                        s.dmRelays.map { arrayOf("relay", it) },
                        extraRelays = (RelayDefaults.indexerRelays + s.dmRelays).distinct(),
                        notifyTagged = false,
                    )
                    relayRepository.applyOwn(event)
                }
                _state.update { it.copy(savedRelays = s.relays, savedDmRelays = s.dmRelays) }
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

class RelaysActions(
    val onBack: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val setRelayInput: (String) -> Unit = {},
    val addRelay: () -> Unit = {},
    val removeRelay: (String) -> Unit = {},
    val toggle: (url: String, read: Boolean) -> Unit = { _, _ -> },
    val setDmInput: (String) -> Unit = {},
    val addDmRelay: () -> Unit = {},
    val removeDmRelay: (String) -> Unit = {},
    val discard: () -> Unit = {},
    val save: () -> Unit = {},
)

@Composable
fun RelaysRoute(onBack: () -> Unit) {
    val viewModel: RelaysViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessageEffect(viewModel.messages)
    RelaysScreen(
        state,
        RelaysActions(
            onBack = onBack,
            onLogin = viewModel::requestLogin,
            onRetry = viewModel::refresh,
            setRelayInput = viewModel::setRelayInput,
            addRelay = viewModel::addRelay,
            removeRelay = viewModel::removeRelay,
            toggle = viewModel::toggle,
            setDmInput = viewModel::setDmInput,
            addDmRelay = viewModel::addDmRelay,
            removeDmRelay = viewModel::removeDmRelay,
            discard = viewModel::discard,
            save = viewModel::save,
        ),
    )
}

@Composable
private fun addErrorText(error: AddError?, invalid: Int, duplicate: Int): String? = when (error) {
    AddError.INVALID -> stringResource(invalid)
    AddError.DUPLICATE -> stringResource(duplicate)
    null -> null
}

@Composable
fun RelaysScreen(state: RelaysUiState, actions: RelaysActions) {
    val relayError = addErrorText(state.relayError, R.string.settings_relay_invalid, R.string.settings_relay_duplicate)
    val dmError = addErrorText(state.dmError, R.string.settings_relay_invalid, R.string.settings_relay_duplicate)
    SettingsScaffold(
        title = stringResource(R.string.settings_relays),
        onBack = actions.onBack,
        bottomBar = if (state.loggedIn && state.dirty) {
            { UnsavedBar(state.saving, state.hasWriteRelay, actions.discard, actions.save) }
        } else {
            null
        },
    ) {
        when {
            !state.loggedIn -> item { LoginRequired(actions.onLogin) }
            state.loading -> item { SettingsListSkeleton(rows = 5) }
            state.loadFailed -> item { ErrorState(message = stringResource(R.string.settings_load_failed), onRetry = actions.onRetry) }
            else -> {
                item { SectionHeader(stringResource(R.string.settings_relays_nip65)) }
                item { HelpText(stringResource(R.string.settings_relays_nip65_help)) }
                if (state.usingDefaults) item { HelpText(stringResource(R.string.settings_relays_defaults)) }
                if (state.relays.isEmpty()) item { HelpText(stringResource(R.string.settings_relays_empty)) }
                items(state.relays.size, key = { "r" + state.relays[it].url }) { i ->
                    RelayRow(state.relays[i], enabled = !state.saving, actions)
                }
                if (state.relays.isNotEmpty() && !state.hasWriteRelay) {
                    item {
                        Text(
                            stringResource(R.string.settings_relays_need_write),
                            style = LuminaTheme.typography.caption,
                            color = LuminaTheme.colors.destructive,
                            modifier = Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                        )
                    }
                }
                item {
                    AddField(
                        value = state.relayInput,
                        onValueChange = actions.setRelayInput,
                        label = stringResource(R.string.settings_relay_add_label),
                        placeholder = stringResource(R.string.settings_relay_placeholder),
                        onAdd = actions.addRelay,
                        error = relayError,
                        enabled = !state.saving,
                    )
                }

                item { SectionHeader(stringResource(R.string.settings_dm_relays)) }
                item { HelpText(stringResource(R.string.settings_dm_relays_help)) }
                if (state.dmRelays.isEmpty()) item { HelpText(stringResource(R.string.settings_dm_relays_empty)) }
                items(state.dmRelays.size, key = { "d" + state.dmRelays[it] }) { i ->
                    UrlRow(state.dmRelays[i], LucideGlyphs.Mail) {
                        RemoveButton(state.dmRelays[i], { actions.removeDmRelay(state.dmRelays[i]) }, enabled = !state.saving)
                    }
                }
                item {
                    AddField(
                        value = state.dmInput,
                        onValueChange = actions.setDmInput,
                        label = stringResource(R.string.settings_relay_add_label),
                        placeholder = stringResource(R.string.settings_relay_placeholder),
                        onAdd = actions.addDmRelay,
                        error = dmError,
                        enabled = !state.saving,
                    )
                }
            }
        }
    }
}

@Composable
private fun RelayRow(entry: RelayEntry, enabled: Boolean, actions: RelaysActions) {
    Column(Modifier.fillMaxWidth()) {
        UrlRow(entry.url, LucideGlyphs.Globe) {
            RemoveButton(entry.url, { actions.removeRelay(entry.url) }, enabled = enabled)
        }
        Row(
            Modifier.padding(start = LuminaDimens.gutter + 38.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LuminaChip(stringResource(R.string.settings_relay_read), entry.read, { if (enabled) actions.toggle(entry.url, true) })
            LuminaChip(stringResource(R.string.settings_relay_write), entry.write, { if (enabled) actions.toggle(entry.url, false) })
        }
    }
}

/** A URL with an icon and trailing actions. */
@Composable
internal fun UrlRow(
    url: String,
    glyph: rocks.lumina.app.core.designsystem.icon.LucideGlyph,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = LuminaDimens.gutter, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LuminaIcon(glyph, null, tint = LuminaTheme.colors.mutedForeground)
        Column(Modifier.weight(1f)) {
            Text(
                url.removeSuffix("/"),
                style = LuminaTheme.typography.body,
                color = LuminaTheme.colors.foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.invoke()
        }
        trailing()
    }
}

private val previewState = RelaysUiState(
    relays = listOf(
        RelayEntry("wss://relay.ditto.pub/", read = true, write = true),
        RelayEntry("wss://nos.lol/", read = false, write = true),
        RelayEntry("wss://relay.damus.io/", read = true, write = false),
    ),
    savedRelays = listOf(RelayEntry("wss://relay.ditto.pub/", read = true, write = true)),
    dmRelays = listOf("wss://auth.nostr1.com/"),
    savedDmRelays = listOf("wss://auth.nostr1.com/"),
    relayInput = "relay",
    relayError = AddError.INVALID,
)

@Preview(name = "Relays – light")
@Composable
private fun RelaysLightPreview() = LuminaTheme(dark = false) { RelaysScreen(previewState, RelaysActions()) }

@Preview(name = "Relays – dark")
@Composable
private fun RelaysDarkPreview() = LuminaTheme(dark = true) { RelaysScreen(previewState.copy(saving = true), RelaysActions()) }

@Preview(name = "Relays loading – light")
@Composable
private fun RelaysLoadingPreview() = LuminaTheme(dark = false) { RelaysScreen(RelaysUiState(loading = true), RelaysActions()) }

@Preview(name = "Relays error – dark")
@Composable
private fun RelaysErrorPreview() = LuminaTheme(dark = true) { RelaysScreen(RelaysUiState(loadFailed = true), RelaysActions()) }
