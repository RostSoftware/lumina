package rocks.lumina.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
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
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaChip
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.LuminaTextField
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.formatSats
import rocks.lumina.app.core.ui.util.toUiMessage
import rocks.lumina.app.core.wallet.NwcRepository
import rocks.lumina.app.core.wallet.WalletInfo

sealed interface BalanceState {
    data object Loading : BalanceState
    data class Value(val sats: Long) : BalanceState
    data object Unavailable : BalanceState
}

data class WalletUiState(
    val loggedIn: Boolean = true,
    val wallet: WalletInfo? = null,
    val input: String = "",
    val inputInvalid: Boolean = false,
    val connecting: Boolean = false,
    val balance: BalanceState = BalanceState.Loading,
    val defaultZap: Long = 21,
    val customZap: String = "",
    val customZapInvalid: Boolean = false,
)

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val accountManager: AccountManager,
    private val nwc: NwcRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WalletUiState(loggedIn = accountManager.activePubkey != null))
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            nwc.wallet.collect { wallet ->
                _state.update { it.copy(wallet = wallet) }
                if (wallet != null) loadBalance()
            }
        }
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(defaultZap = s.defaultZapSats) } }
        }
    }

    fun requestLogin() = accountManager.requestLogin()

    fun loadBalance() {
        _state.update { it.copy(balance = BalanceState.Loading) }
        viewModelScope.launch {
            val sats = try {
                nwc.balanceSats()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _state.update { it.copy(balance = if (sats != null) BalanceState.Value(sats) else BalanceState.Unavailable) }
        }
    }

    fun setInput(value: String) = _state.update { it.copy(input = value, inputInvalid = false) }

    fun onScanned(content: String?) {
        val value = content?.trim()?.removePrefix("nostr:") ?: return
        setInput(value)
        connect()
    }

    fun connect() {
        val s = _state.value
        if (s.connecting) return
        if (!WalletInput.looksLikeNwc(s.input)) {
            _state.update { it.copy(inputInvalid = true) }
            return
        }
        _state.update { it.copy(connecting = true) }
        viewModelScope.launch {
            try {
                nwc.connect(s.input)
                _state.update { it.copy(input = "") }
                _messages.trySend(UiMessage.Res(R.string.settings_wallet_connected))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend(e.toUiMessage())
            } finally {
                _state.update { it.copy(connecting = false) }
            }
        }
    }

    fun disconnect() {
        nwc.disconnect()
        _messages.trySend(UiMessage.Res(R.string.settings_wallet_disconnected))
    }

    fun setDefaultZap(sats: Long) {
        settings.setDefaultZap(sats)
        _state.update { it.copy(customZap = "", customZapInvalid = false) }
    }

    fun setCustomZap(value: String) = _state.update { it.copy(customZap = value.filter { c -> c.isDigit() }, customZapInvalid = false) }

    fun applyCustomZap() {
        val sats = ZapAmount.parse(_state.value.customZap)
        if (sats == null) {
            _state.update { it.copy(customZapInvalid = true) }
        } else {
            setDefaultZap(sats)
        }
    }
}

class WalletActions(
    val onBack: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val setInput: (String) -> Unit = {},
    val paste: () -> Unit = {},
    val scan: () -> Unit = {},
    val connect: () -> Unit = {},
    val disconnect: () -> Unit = {},
    val refreshBalance: () -> Unit = {},
    val setDefaultZap: (Long) -> Unit = {},
    val setCustomZap: (String) -> Unit = {},
    val applyCustomZap: () -> Unit = {},
)

@Composable
fun WalletRoute(onBack: () -> Unit) {
    val viewModel: WalletViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    MessageEffect(viewModel.messages)
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result -> viewModel.onScanned(result.contents) }
    WalletScreen(
        state,
        WalletActions(
            onBack = onBack,
            onLogin = viewModel::requestLogin,
            setInput = viewModel::setInput,
            paste = { pasteText(context)?.let { viewModel.setInput(it.trim()) } },
            scan = {
                scanLauncher.launch(
                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setBeepEnabled(false).setOrientationLocked(false),
                )
            },
            connect = viewModel::connect,
            disconnect = viewModel::disconnect,
            refreshBalance = viewModel::loadBalance,
            setDefaultZap = viewModel::setDefaultZap,
            setCustomZap = viewModel::setCustomZap,
            applyCustomZap = viewModel::applyCustomZap,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletScreen(state: WalletUiState, actions: WalletActions) {
    SettingsScaffold(stringResource(R.string.settings_wallet), actions.onBack) {
        if (!state.loggedIn) {
            item { LoginRequired(actions.onLogin) }
            return@SettingsScaffold
        }
        val wallet = state.wallet
        if (wallet == null) {
            item { SectionHeader(stringResource(R.string.settings_wallet_connect_title)) }
            item { HelpText(stringResource(R.string.settings_wallet_connect_body)) }
            item { NwcInput(state, actions) }
        } else {
            item { SectionHeader(stringResource(R.string.settings_wallet_connected)) }
            item { SettingsRow(stringResource(R.string.settings_wallet_relay), subtitle = wallet.relay, glyph = LucideGlyphs.Globe) }
            wallet.lud16?.let { lud16 ->
                item { SettingsRow(stringResource(R.string.settings_wallet_address), subtitle = lud16, glyph = LucideGlyphs.Zap) }
            }
            item {
                val balance = when (val b = state.balance) {
                    BalanceState.Loading -> null
                    is BalanceState.Value -> stringResource(R.string.settings_wallet_balance_value, formatSats(b.sats))
                    BalanceState.Unavailable -> stringResource(R.string.settings_wallet_balance_unavailable)
                }
                SettingsRow(
                    stringResource(R.string.settings_wallet_balance),
                    subtitle = balance,
                    glyph = LucideGlyphs.Wallet,
                    trailing = {
                        if (state.balance == BalanceState.Loading) {
                            Spinner(size = 18.dp)
                        } else {
                            LuminaIconButton(LucideGlyphs.RefreshCw, stringResource(R.string.settings_wallet_balance), actions.refreshBalance, iconSize = 20.dp)
                        }
                    },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_wallet_disconnect),
                    glyph = LucideGlyphs.Unplug,
                    titleColor = LuminaTheme.colors.destructive,
                    onClick = actions.disconnect,
                    trailing = {},
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_zap_default)) }
        item {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RelayDefaults.zapPresets.forEach { sats ->
                    LuminaChip(stringResource(R.string.settings_zap_sats, formatSats(sats)), state.defaultZap == sats, { actions.setDefaultZap(sats) })
                }
                if (state.defaultZap !in RelayDefaults.zapPresets) {
                    LuminaChip(stringResource(R.string.settings_zap_sats, formatSats(state.defaultZap)), true, {})
                }
            }
        }
        item {
            AddField(
                value = state.customZap,
                onValueChange = actions.setCustomZap,
                label = stringResource(R.string.settings_zap_custom),
                onAdd = actions.applyCustomZap,
                error = if (state.customZapInvalid) stringResource(R.string.settings_zap_invalid) else null,
                keyboardType = KeyboardType.Number,
                glyph = LucideGlyphs.Zap,
            )
        }
    }
}

@Composable
private fun NwcInput(state: WalletUiState, actions: WalletActions) {
    var visible by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LuminaTextField(
            value = state.input,
            onValueChange = actions.setInput,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.settings_wallet_uri_label),
            placeholder = stringResource(R.string.settings_wallet_placeholder),
            isError = state.inputInvalid,
            supportingText = if (state.inputInvalid) stringResource(R.string.settings_wallet_invalid) else stringResource(R.string.settings_wallet_secret_note),
            enabled = !state.connecting,
            leadingGlyph = LucideGlyphs.Wallet,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done, autoCorrectEnabled = false),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailing = {
                androidx.compose.foundation.layout.Row {
                    LuminaIconButton(
                        if (visible) LucideGlyphs.EyeOff else LucideGlyphs.Eye,
                        stringResource(if (visible) R.string.settings_hide_secret else R.string.settings_show_secret),
                        { visible = !visible },
                        iconSize = 20.dp,
                    )
                    LuminaIconButton(LucideGlyphs.ClipboardPaste, stringResource(R.string.settings_paste), actions.paste, iconSize = 20.dp)
                }
            },
        )
        LuminaButton(
            stringResource(R.string.settings_wallet_connect),
            actions.connect,
            loading = state.connecting,
            enabled = !state.connecting && state.input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        LuminaButton(
            stringResource(R.string.settings_scan),
            actions.scan,
            style = ButtonStyle.Outline,
            leadingIcon = LucideGlyphs.ScanLine,
            enabled = !state.connecting,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val previewWallet = WalletInfo(PREVIEW_PK, "wss://relay.getalby.com/v1", "alice@getalby.com")

@Preview(name = "Wallet connected – light")
@Composable
private fun WalletLightPreview() = LuminaTheme(dark = false) {
    WalletScreen(WalletUiState(wallet = previewWallet, balance = BalanceState.Value(21_000), defaultZap = 100), WalletActions())
}

@Preview(name = "Wallet connected – dark")
@Composable
private fun WalletDarkPreview() = LuminaTheme(dark = true) {
    WalletScreen(WalletUiState(wallet = previewWallet, balance = BalanceState.Loading, defaultZap = 777), WalletActions())
}

@Preview(name = "Wallet connect – light")
@Composable
private fun WalletConnectPreview() = LuminaTheme(dark = false) {
    WalletScreen(WalletUiState(input = "nostr+walletconnect://abc", inputInvalid = true), WalletActions())
}

@Preview(name = "Wallet connect – dark")
@Composable
private fun WalletConnectDarkPreview() = LuminaTheme(dark = true) {
    WalletScreen(WalletUiState(connecting = true, input = "nostr+walletconnect://abc"), WalletActions())
}
