package rocks.lumina.app.core.signer

import android.content.Intent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects NIP-55 signers (Amber) to the foreground Activity. When a request needs manual approval,
 * Quartz's `NostrSignerExternal` asks for an Activity launch; `MainActivity` [attach]es an
 * `ActivityResultLauncher` and forwards results back through [onResult].
 */
@Singleton
public class ExternalSignerBridge @Inject constructor() {
    @Volatile private var launcher: ((Intent) -> Unit)? = null
    @Volatile private var resultHandler: ((Intent) -> Unit)? = null

    public fun attach(launcher: (Intent) -> Unit) {
        this.launcher = launcher
    }

    public fun detach(launcher: (Intent) -> Unit) {
        if (this.launcher === launcher) this.launcher = null
    }

    public val hasActivity: Boolean get() = launcher != null

    /** Called by the signer to show the approval UI. */
    public fun launch(intent: Intent) {
        launcher?.invoke(intent)
    }

    internal fun setResultHandler(handler: ((Intent) -> Unit)?) {
        resultHandler = handler
    }

    /** Called by the Activity with the signer's result Intent. */
    public fun onResult(data: Intent) {
        resultHandler?.invoke(data)
    }
}
