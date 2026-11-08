package rocks.lumina.app.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow

/** Collects [messages] and shows them in the app snackbar. */
@Composable
public fun MessageEffect(messages: Flow<UiMessage>) {
    val host = LocalSnackbarHostState.current
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { host.showSnackbar(it.resolve(context)) }
    }
}
