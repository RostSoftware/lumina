package rocks.lumina.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.lumina.app.core.designsystem.component.ButtonStyle
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.LuminaBottomSheet
import rocks.lumina.app.core.designsystem.component.LuminaButton
import rocks.lumina.app.core.designsystem.component.LuminaIconButton
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.component.Spinner
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaShapes
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.core.signer.Account
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.signer.SignerType
import rocks.lumina.app.core.ui.sheet.QrCode
import rocks.lumina.app.core.ui.user.UserRow
import rocks.lumina.app.core.ui.util.LocalSnackbarHostState
import rocks.lumina.app.core.ui.util.MessageEffect
import rocks.lumina.app.core.ui.util.UiMessage
import rocks.lumina.app.core.ui.util.toUiMessage

/** Which confirmation sheet is open. */
enum class AccountDialog { None, ConfirmBackup, ShowKey, ConfirmLogout }

data class AccountUiState(
    val accounts: List<Account> = emptyList(),
    val active: Account? = null,
    val npub: String = "",
    val showQr: Boolean = false,
    val dialog: AccountDialog = AccountDialog.None,
    /** Only set while the "Your secret key" sheet is open. */
    val nsec: String? = null,
    val switching: String? = null,
    val loggingOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(private val accountManager: AccountManager) : ViewModel() {
    private val _ui = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _ui.asStateFlow()

    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()

    private val _closed = Channel<Unit>(Channel.CONFLATED)
    /** Emits when the last account was logged out. */
    val closed: Flow<Unit> = _closed.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(accountManager.accounts, accountManager.active) { list, active -> list to active?.account }.collect { (list, active) ->
                _ui.update { s ->
                    val changed = s.active?.pubkey != active?.pubkey
                    s.copy(
                        accounts = list,
                        active = active,
                        npub = active?.pubkey?.let { runCatching { NostrLinks.npub(it) }.getOrDefault("") }.orEmpty(),
                        showQr = if (changed) false else s.showQr,
                        dialog = if (changed) AccountDialog.None else s.dialog,
                        nsec = if (changed) null else s.nsec,
                    )
                }
            }
        }
    }

    fun switchTo(pubkey: String) {
        if (pubkey == _ui.value.active?.pubkey || _ui.value.switching != null) return
        _ui.update { it.copy(switching = pubkey) }
        viewModelScope.launch {
            try {
                accountManager.switchTo(pubkey)
                _messages.trySend(UiMessage.Res(R.string.settings_switched))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend(e.toUiMessage())
            } finally {
                _ui.update { it.copy(switching = null) }
            }
        }
    }

    fun toggleQr() = _ui.update { it.copy(showQr = !it.showQr) }

    fun askBackup() = _ui.update { it.copy(dialog = AccountDialog.ConfirmBackup) }

    fun askLogout() = _ui.update { it.copy(dialog = AccountDialog.ConfirmLogout) }

    fun dismiss() = _ui.update { it.copy(dialog = AccountDialog.None, nsec = null) }

    fun revealKey() {
        val account = _ui.value.active ?: return
        if (account.type != SignerType.INTERNAL) return
        viewModelScope.launch {
            val nsec = withContext(Dispatchers.IO) { runCatching { accountManager.exportNsec(account.pubkey) }.getOrNull() }
            if (nsec == null) {
                _ui.update { it.copy(dialog = AccountDialog.None) }
                _messages.trySend(UiMessage.Res(R.string.settings_backup_unavailable))
            } else {
                _ui.update { it.copy(dialog = AccountDialog.ShowKey, nsec = nsec) }
            }
        }
    }

    fun logout() {
        val account = _ui.value.active ?: return
        if (_ui.value.loggingOut) return
        _ui.update { it.copy(loggingOut = true) }
        viewModelScope.launch {
            try {
                accountManager.logout(account.pubkey)
                _ui.update { it.copy(dialog = AccountDialog.None, nsec = null) }
                if (accountManager.accounts.value.isEmpty()) _closed.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.trySend(e.toUiMessage())
            } finally {
                _ui.update { it.copy(loggingOut = false) }
            }
        }
    }

    fun requestLogin() = accountManager.requestLogin()
}

class AccountActions(
    val onBack: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onLogin: () -> Unit = {},
    val switchTo: (String) -> Unit = {},
    val copy: (text: String, sensitive: Boolean) -> Unit = { _, _ -> },
    val toggleQr: () -> Unit = {},
    val askBackup: () -> Unit = {},
    val revealKey: () -> Unit = {},
    val askLogout: () -> Unit = {},
    val logout: () -> Unit = {},
    val dismiss: () -> Unit = {},
)

@Composable
fun AccountRoute(onBack: () -> Unit, onAddAccount: () -> Unit) {
    val viewModel: AccountViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.settings_copied)
    val currentOnBack by rememberUpdatedState(onBack)
    MessageEffect(viewModel.messages)
    LaunchedEffect(viewModel) { viewModel.closed.collect { currentOnBack() } }
    AccountScreen(
        state,
        AccountActions(
            onBack = onBack,
            onAddAccount = onAddAccount,
            onLogin = viewModel::requestLogin,
            switchTo = viewModel::switchTo,
            copy = { text, sensitive ->
                copyText(context, text, sensitive)
                scope.launch { snackbar.showSnackbar(copied) }
            },
            toggleQr = viewModel::toggleQr,
            askBackup = viewModel::askBackup,
            revealKey = viewModel::revealKey,
            askLogout = viewModel::askLogout,
            logout = viewModel::logout,
            dismiss = viewModel::dismiss,
        ),
    )
}

@Composable
fun AccountScreen(state: AccountUiState, actions: AccountActions) {
    val active = state.active
    SettingsScaffold(stringResource(R.string.settings_account), actions.onBack) {
        if (active == null) {
            item { LoginRequired(actions.onLogin) }
            return@SettingsScaffold
        }

        item { SectionHeader(stringResource(R.string.settings_accounts)) }
        items(state.accounts.size, key = { state.accounts[it].pubkey }) { i ->
            val account = state.accounts[i]
            val isActive = account.pubkey == active.pubkey
            UserRow(
                account.pubkey,
                subtitle = signerLabel(account),
                onClick = if (isActive) null else ({ actions.switchTo(account.pubkey) }),
            ) {
                when {
                    state.switching == account.pubkey -> Spinner(size = 20.dp)
                    isActive -> LuminaIcon(LucideGlyphs.CircleCheck, stringResource(R.string.settings_active), tint = LuminaTheme.colors.accent)
                }
            }
        }
        item { SettingsRow(stringResource(R.string.settings_add_account), glyph = LucideGlyphs.UserPlus, onClick = actions.onAddAccount) }
        item { Hairline() }

        item { SectionHeader(stringResource(R.string.settings_public_key)) }
        item { NpubBlock(state, actions) }

        item { SectionHeader(stringResource(R.string.settings_signer)) }
        item { SignerInfo(active) }
        if (active.type == SignerType.INTERNAL) {
            item {
                SettingsRow(
                    stringResource(R.string.settings_backup),
                    subtitle = stringResource(R.string.settings_backup_sub),
                    glyph = LucideGlyphs.KeyRound,
                    onClick = actions.askBackup,
                )
            }
        }
        item { Hairline(Modifier.padding(top = 8.dp)) }
        item {
            SettingsRow(
                stringResource(R.string.settings_logout),
                subtitle = stringResource(R.string.settings_logout_sub),
                glyph = LucideGlyphs.LogOut,
                titleColor = LuminaTheme.colors.destructive,
                onClick = actions.askLogout,
                trailing = {},
            )
        }
    }

    if (active != null) AccountSheets(state, active, actions)
}

@Composable
private fun signerLabel(account: Account): String = when (account.type) {
    SignerType.INTERNAL -> stringResource(R.string.settings_signer_internal)
    SignerType.AMBER -> stringResource(R.string.settings_signer_amber)
    SignerType.NIP46 -> stringResource(R.string.settings_signer_nip46)
}

@Composable
private fun NpubBlock(state: AccountUiState, actions: AccountActions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().background(LuminaTheme.colors.surfaceMuted, LuminaShapes.control).padding(start = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionContainer(Modifier.weight(1f)) {
                Text(
                    state.npub,
                    style = LuminaTheme.typography.caption.copy(fontFamily = FontFamily.Monospace),
                    color = LuminaTheme.colors.foreground,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
            }
            LuminaIconButton(LucideGlyphs.Copy, stringResource(R.string.settings_copy), { actions.copy(state.npub, false) }, iconSize = 20.dp)
            LuminaIconButton(
                LucideGlyphs.QrCode,
                stringResource(if (state.showQr) R.string.settings_hide_qr else R.string.settings_show_qr),
                actions.toggleQr,
                filled = state.showQr,
                iconSize = 20.dp,
            )
        }
        if (state.showQr && state.npub.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                QrCode("nostr:${state.npub}", size = 220.dp)
            }
        }
    }
}

@Composable
private fun SignerInfo(account: Account) {
    when (account.type) {
        SignerType.INTERNAL -> SettingsRow(
            stringResource(R.string.settings_signer_internal),
            subtitle = stringResource(R.string.settings_signer_internal_sub),
            glyph = LucideGlyphs.Lock,
        )
        SignerType.AMBER -> SettingsRow(
            stringResource(R.string.settings_signer_amber),
            subtitle = account.packageName,
            glyph = LucideGlyphs.Smartphone,
        )
        SignerType.NIP46 -> SettingsRow(
            stringResource(R.string.settings_signer_nip46),
            subtitle = buildList {
                account.remotePubkey?.let { add(it.take(8) + "…" + it.takeLast(8)) }
                addAll(account.bunkerRelays.map { it.removeSuffix("/") })
            }.joinToString("\n").ifEmpty { null },
            glyph = LucideGlyphs.Link,
        )
    }
}

@Composable
private fun AccountSheets(state: AccountUiState, active: Account, actions: AccountActions) {
    when (state.dialog) {
        AccountDialog.None -> Unit
        AccountDialog.ConfirmBackup -> LuminaBottomSheet(onDismiss = actions.dismiss, title = stringResource(R.string.settings_backup_confirm_title)) {
            SheetBody(stringResource(R.string.settings_backup_confirm_body))
            SheetButtons {
                LuminaButton(stringResource(R.string.settings_cancel), actions.dismiss, style = ButtonStyle.Outline, modifier = Modifier.weight(1f))
                LuminaButton(stringResource(R.string.settings_backup_show), actions.revealKey, style = ButtonStyle.Destructive, modifier = Modifier.weight(1f))
            }
        }
        AccountDialog.ShowKey -> LuminaBottomSheet(onDismiss = actions.dismiss, title = stringResource(R.string.settings_backup_title)) {
            SecureWindowEffect()
            val nsec = state.nsec.orEmpty()
            SheetBody(stringResource(R.string.settings_backup_warning), destructive = true)
            SelectionContainer(Modifier.padding(horizontal = LuminaDimens.gutter, vertical = 8.dp)) {
                Text(
                    nsec,
                    style = LuminaTheme.typography.body.copy(fontFamily = FontFamily.Monospace),
                    color = LuminaTheme.colors.foreground,
                    modifier = Modifier.fillMaxWidth().background(LuminaTheme.colors.surfaceMuted, LuminaShapes.control).padding(14.dp),
                )
            }
            SheetButtons {
                LuminaButton(
                    stringResource(R.string.settings_copy),
                    { actions.copy(nsec, true) },
                    style = ButtonStyle.Outline,
                    leadingIcon = LucideGlyphs.Copy,
                    modifier = Modifier.weight(1f),
                )
                LuminaButton(stringResource(R.string.settings_done), actions.dismiss, modifier = Modifier.weight(1f))
            }
        }
        AccountDialog.ConfirmLogout -> LuminaBottomSheet(onDismiss = actions.dismiss, title = stringResource(R.string.settings_logout_confirm_title)) {
            SheetBody(stringResource(R.string.settings_logout_confirm_body))
            if (active.type == SignerType.INTERNAL) SheetBody(stringResource(R.string.settings_logout_internal_warning), destructive = true)
            SheetButtons {
                LuminaButton(stringResource(R.string.settings_cancel), actions.dismiss, style = ButtonStyle.Outline, modifier = Modifier.weight(1f), enabled = !state.loggingOut)
                LuminaButton(
                    stringResource(R.string.settings_logout),
                    actions.logout,
                    style = ButtonStyle.Destructive,
                    loading = state.loggingOut,
                    enabled = !state.loggingOut,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SheetBody(text: String, destructive: Boolean = false) {
    Text(
        text,
        style = LuminaTheme.typography.body,
        color = if (destructive) LuminaTheme.colors.destructive else LuminaTheme.colors.mutedForeground,
        modifier = Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 6.dp),
    )
}

@Composable
private fun SheetButtons(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

private val previewAccounts = listOf(
    Account(PREVIEW_PK, SignerType.INTERNAL),
    Account(PREVIEW_PK2, SignerType.NIP46, remotePubkey = PREVIEW_PK2, bunkerRelays = listOf("wss://relay.nsec.app/")),
)

@Preview(name = "Account – light")
@Composable
private fun AccountLightPreview() = LuminaTheme(dark = false) {
    AccountScreen(
        AccountUiState(previewAccounts, previewAccounts[0], npub = "npub180cvv07tjdrrgpa0j7j7tmnyl2yr6yr7l8j4s3evf6u64th6gkwsyjh6w6", showQr = true),
        AccountActions(),
    )
}

@Preview(name = "Account – dark")
@Composable
private fun AccountDarkPreview() = LuminaTheme(dark = true) {
    AccountScreen(
        AccountUiState(previewAccounts, previewAccounts[1], npub = "npub1sg6plzptd64u62a878hep2kev88swjh3tw00gjsfl8f237lmu63q0uf63m", switching = PREVIEW_PK),
        AccountActions(),
    )
}

@Preview(name = "Account logged out – light")
@Composable
private fun AccountLoggedOutPreview() = LuminaTheme(dark = false) { AccountScreen(AccountUiState(), AccountActions()) }
