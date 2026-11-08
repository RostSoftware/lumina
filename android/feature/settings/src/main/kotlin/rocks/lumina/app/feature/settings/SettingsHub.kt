package rocks.lumina.app.feature.settings

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import rocks.lumina.app.core.data.AppTheme
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.component.EmptyState
import rocks.lumina.app.core.designsystem.component.Hairline
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.theme.LuminaDimens
import rocks.lumina.app.core.designsystem.theme.LuminaTheme
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.user.UserRow

/** Sub-pages reachable from the settings hub. */
enum class SettingsPage { Relays, Media, Wallet, Appearance, Content, Account }

data class SettingsHubUiState(
    val activePubkey: String? = null,
    val theme: AppTheme = AppTheme.SYSTEM,
    val version: String = "",
)

@HiltViewModel
class SettingsHubViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val accountManager: AccountManager,
    settings: SettingsRepository,
) : ViewModel() {
    private val version: String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    val state: StateFlow<SettingsHubUiState> = combine(accountManager.active, settings.settings) { active, s ->
        SettingsHubUiState(active?.pubkey, s.theme, version)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsHubUiState(accountManager.activePubkey, version = version))

    fun requestLogin() = accountManager.requestLogin()
}

@Composable
fun SettingsRoute(onBack: () -> Unit, onOpen: (SettingsPage) -> Unit, onEditProfile: () -> Unit) {
    val viewModel: SettingsHubViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, onBack, onOpen, onEditProfile, viewModel::requestLogin)
}

@Composable
fun SettingsScreen(
    state: SettingsHubUiState,
    onBack: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onEditProfile: () -> Unit,
    onLogin: () -> Unit,
) {
    val themeLabel = stringResource(
        when (state.theme) {
            AppTheme.SYSTEM -> R.string.settings_theme_system
            AppTheme.LIGHT -> R.string.settings_theme_light
            AppTheme.DARK -> R.string.settings_theme_dark
        },
    )
    SettingsScaffold(stringResource(R.string.settings_title), onBack) {
        val pk = state.activePubkey
        if (pk == null) {
            item {
                EmptyState(
                    title = stringResource(R.string.settings_logged_out_title),
                    message = stringResource(R.string.settings_logged_out_body),
                    glyph = LucideGlyphs.User,
                    actionLabel = stringResource(R.string.settings_log_in),
                    onAction = onLogin,
                )
            }
            item { Hairline() }
            item {
                SettingsRow(stringResource(R.string.settings_appearance), subtitle = themeLabel, glyph = LucideGlyphs.Palette, onClick = { onOpen(SettingsPage.Appearance) })
            }
        } else {
            item { UserRow(pk, onClick = { onOpen(SettingsPage.Account) }) }
            item { Hairline() }
            item { SettingsRow(stringResource(R.string.settings_edit_profile), glyph = LucideGlyphs.Pencil, onClick = onEditProfile) }
            item { SectionHeader(stringResource(R.string.settings_section_network)) }
            item {
                SettingsRow(
                    stringResource(R.string.settings_relays),
                    subtitle = stringResource(R.string.settings_relays_sub),
                    glyph = LucideGlyphs.Globe,
                    onClick = { onOpen(SettingsPage.Relays) },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_media),
                    subtitle = stringResource(R.string.settings_media_sub),
                    glyph = LucideGlyphs.Server,
                    onClick = { onOpen(SettingsPage.Media) },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_wallet),
                    subtitle = stringResource(R.string.settings_wallet_sub),
                    glyph = LucideGlyphs.Wallet,
                    onClick = { onOpen(SettingsPage.Wallet) },
                )
            }
            item { SectionHeader(stringResource(R.string.settings_section_general)) }
            item {
                SettingsRow(stringResource(R.string.settings_appearance), subtitle = themeLabel, glyph = LucideGlyphs.Palette, onClick = { onOpen(SettingsPage.Appearance) })
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_content),
                    subtitle = stringResource(R.string.settings_content_sub),
                    glyph = LucideGlyphs.ShieldCheck,
                    onClick = { onOpen(SettingsPage.Content) },
                )
            }
            item {
                SettingsRow(
                    stringResource(R.string.settings_account),
                    subtitle = stringResource(R.string.settings_account_sub),
                    glyph = LucideGlyphs.User,
                    onClick = { onOpen(SettingsPage.Account) },
                )
            }
        }
        if (state.version.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.settings_version, state.version),
                    style = LuminaTheme.typography.caption,
                    color = LuminaTheme.colors.mutedForeground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = LuminaDimens.gutter, vertical = 24.dp),
                )
            }
        }
    }
}

@Preview(name = "Settings – light")
@Composable
private fun SettingsLightPreview() = LuminaTheme(dark = false) {
    SettingsScreen(SettingsHubUiState(PREVIEW_PK, AppTheme.SYSTEM, "1.0.0"), {}, {}, {}, {})
}

@Preview(name = "Settings – dark")
@Composable
private fun SettingsDarkPreview() = LuminaTheme(dark = true) {
    SettingsScreen(SettingsHubUiState(PREVIEW_PK, AppTheme.DARK, "1.0.0"), {}, {}, {}, {})
}

@Preview(name = "Settings logged out – light")
@Composable
private fun SettingsLoggedOutPreview() = LuminaTheme(dark = false) {
    SettingsScreen(SettingsHubUiState(null, AppTheme.LIGHT, "1.0.0"), {}, {}, {}, {})
}

@Preview(name = "Settings logged out – dark")
@Composable
private fun SettingsLoggedOutDarkPreview() = LuminaTheme(dark = true) {
    SettingsScreen(SettingsHubUiState(null, AppTheme.DARK, "1.0.0"), {}, {}, {}, {})
}
