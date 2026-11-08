package rocks.lumina.app.feature.settings

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import rocks.lumina.app.core.data.AppTheme
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.designsystem.component.SectionHeader
import rocks.lumina.app.core.designsystem.component.SettingsRow
import rocks.lumina.app.core.designsystem.icon.LucideGlyphs
import rocks.lumina.app.core.designsystem.icon.LuminaIcon
import rocks.lumina.app.core.designsystem.theme.LuminaTheme

data class AppearanceUiState(val theme: AppTheme = AppTheme.SYSTEM)

@HiltViewModel
class AppearanceViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<AppearanceUiState> = settings.settings.map { AppearanceUiState(it.theme) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppearanceUiState(settings.settings.value.theme))

    fun setTheme(theme: AppTheme) = settings.setTheme(theme)
}

@Composable
fun AppearanceRoute(onBack: () -> Unit) {
    val viewModel: AppearanceViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppearanceScreen(state, onBack, viewModel::setTheme)
}

@Composable
fun AppearanceScreen(state: AppearanceUiState, onBack: () -> Unit, onSelect: (AppTheme) -> Unit) {
    SettingsScaffold(stringResource(R.string.settings_appearance), onBack) {
        item { SectionHeader(stringResource(R.string.settings_theme)) }
        item {
            androidx.compose.foundation.layout.Column(Modifier.selectableGroup()) {
                ThemeOption(AppTheme.SYSTEM, state.theme, onSelect)
                ThemeOption(AppTheme.LIGHT, state.theme, onSelect)
                ThemeOption(AppTheme.DARK, state.theme, onSelect)
            }
        }
    }
}

@Composable
private fun ThemeOption(theme: AppTheme, current: AppTheme, onSelect: (AppTheme) -> Unit) {
    val selected = theme == current
    val (title, glyph) = when (theme) {
        AppTheme.SYSTEM -> stringResource(R.string.settings_theme_system) to LucideGlyphs.Monitor
        AppTheme.LIGHT -> stringResource(R.string.settings_theme_light) to LucideGlyphs.Sun
        AppTheme.DARK -> stringResource(R.string.settings_theme_dark) to LucideGlyphs.Moon
    }
    SettingsRow(
        title = title,
        subtitle = if (theme == AppTheme.SYSTEM) stringResource(R.string.settings_theme_system_sub) else null,
        glyph = glyph,
        modifier = Modifier.semantics {
            this.selected = selected
            role = Role.RadioButton
        },
        onClick = { onSelect(theme) },
        trailing = {
            if (selected) LuminaIcon(LucideGlyphs.Check, null, tint = LuminaTheme.colors.accent, size = 20.dp)
        },
    )
}

@Preview(name = "Appearance – light")
@Composable
private fun AppearanceLightPreview() = LuminaTheme(dark = false) {
    AppearanceScreen(AppearanceUiState(AppTheme.SYSTEM), {}, {})
}

@Preview(name = "Appearance – dark")
@Composable
private fun AppearanceDarkPreview() = LuminaTheme(dark = true) {
    AppearanceScreen(AppearanceUiState(AppTheme.DARK), {}, {})
}
