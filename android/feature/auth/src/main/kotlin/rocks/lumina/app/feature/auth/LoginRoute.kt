package rocks.lumina.app.feature.auth

import androidx.compose.ui.res.stringResource
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.vitorpamplona.quartz.nip55AndroidSigner.client.ExternalSignerLogin
import kotlinx.coroutines.launch
import rocks.lumina.app.core.ui.util.LocalSnackbarHostState
import rocks.lumina.app.core.ui.util.MessageEffect

private const val AMBER_RELEASES = "https://github.com/greenart7c3/Amber/releases"

/**
 * Full login flow: Amber (NIP-55) → bunker → nostrconnect → nsec / new account.
 * [onDone] runs after a successful login (and onboarding for new accounts); [onBack] closes the flow.
 */
@Composable
fun LoginRoute(onDone: () -> Unit, onBack: () -> Unit) {
    val viewModel: LoginViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val currentOnDone by rememberUpdatedState(onDone)

    MessageEffect(viewModel.messages)
    LaunchedEffect(viewModel) { viewModel.done.collect { currentOnDone() } }
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshSigners()
        onPauseOrDispose { }
    }
    BackHandler(enabled = state.step != LoginStep.Choose) { viewModel.back() }

    val amberLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onAmberResult(result.resultCode == Activity.RESULT_OK, result.data)
    }
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        viewModel.onScanned(result.contents)
    }
    val scanPrompt = stringResource(R.string.auth_scan_prompt)
    val copiedText = stringResource(R.string.auth_copied)
    val noAppText = stringResource(R.string.auth_no_app)

    fun toast(text: String) {
        scope.launch { snackbar.showSnackbar(text) }
    }

    fun openUri(uri: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            toast(noAppText)
        }
    }

    val actions = remember(viewModel, onBack) {
        LoginActions(
            close = onBack,
            back = { viewModel.back() },
            open = viewModel::open,
            amberLogin = { pkg ->
                val intent = ExternalSignerLogin.createIntent(AmberPermissions.all, pkg)
                try {
                    viewModel.amberLaunched()
                    amberLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    viewModel.amberLaunchFailed()
                }
            },
            getAmber = { openUri(AMBER_RELEASES) },
            setBunker = viewModel::setBunkerInput,
            pasteBunker = { pasteText(context)?.let { viewModel.setBunkerInput(it.trim()) } },
            scanBunker = {
                val options = ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt(scanPrompt)
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
                try {
                    scanLauncher.launch(options)
                } catch (_: ActivityNotFoundException) {
                    toast(noAppText)
                }
            },
            connectBunker = viewModel::connectBunker,
            cancelBunker = viewModel::cancelBunker,
            openNostrConnect = { uri -> openUri(uri) },
            copyNostrConnect = { uri ->
                copyText(context, uri)
                toast(copiedText)
            },
            retryNostrConnect = viewModel::startNostrConnect,
            setNsec = viewModel::setNsecInput,
            loginNsec = viewModel::loginWithNsec,
            createAccount = viewModel::createAccount,
            setDisplayName = viewModel::setDisplayName,
            setAbout = viewModel::setAbout,
            saveProfile = viewModel::saveProfile,
            skipProfile = viewModel::skipProfile,
            revealBackup = viewModel::revealBackup,
            copyBackup = { nsec ->
                copyText(context, nsec, sensitive = true)
                viewModel.backupCopied()
                toast(copiedText)
            },
            finishBackup = viewModel::finishBackup,
        )
    }

    LoginScreen(state, actions)
}
