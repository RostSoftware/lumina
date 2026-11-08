package rocks.lumina.app.core.ui.util

import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf
import rocks.lumina.app.core.data.PublishFailedException
import rocks.lumina.app.core.signer.SignerFailure
import rocks.lumina.app.core.signer.SignerFailureException
import rocks.lumina.app.core.signer.toSignerFailure
import rocks.lumina.app.core.ui.R
import rocks.lumina.app.core.wallet.LnurlException
import rocks.lumina.app.core.wallet.WalletException

/** A snackbar message: a string resource with args, or literal text from a remote service. */
public sealed interface UiMessage {
    public data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiMessage
    public data class Text(val text: String) : UiMessage
}

/** Maps any failure to a user-facing message — every publish path shows one, never a silent failure. */
public fun Throwable.toUiMessage(): UiMessage = when (this) {
    is PublishFailedException -> UiMessage.Res(R.string.err_publish_failed)
    is WalletException -> UiMessage.Text(message ?: "Wallet error")
    is LnurlException -> UiMessage.Text(message ?: "Lightning error")
    is SignerFailureException -> failure.toUiMessage()
    else -> {
        val failure = toSignerFailure()
        if (failure != SignerFailure.OTHER) failure.toUiMessage() else UiMessage.Res(R.string.err_generic)
    }
}

public fun SignerFailure.toUiMessage(): UiMessage = UiMessage.Res(
    when (this) {
        SignerFailure.REJECTED -> R.string.err_rejected
        SignerFailure.TIMED_OUT -> R.string.err_timed_out
        SignerFailure.UNREACHABLE -> R.string.err_unreachable
        SignerFailure.NOT_FOUND -> R.string.err_signer_missing
        SignerFailure.NO_SIGNER -> R.string.err_login_required
        SignerFailure.OTHER -> R.string.err_generic
    },
)

/** App-wide snackbar host, provided by the app shell. */
public val LocalSnackbarHostState: androidx.compose.runtime.ProvidableCompositionLocal<SnackbarHostState> =
    staticCompositionLocalOf { SnackbarHostState() }

public fun UiMessage.resolve(context: android.content.Context): String = when (this) {
    is UiMessage.Res -> context.getString(id, *args.toTypedArray())
    is UiMessage.Text -> text
}
