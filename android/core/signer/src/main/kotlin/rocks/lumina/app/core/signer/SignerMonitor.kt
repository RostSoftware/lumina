package rocks.lumina.app.core.signer

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import com.vitorpamplona.quartz.nip57Zaps.PrivateZapEvent
import com.vitorpamplona.quartz.nip57Zaps.ZapRequestEvent
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

/** Why a signer request failed. Every publish path surfaces one of these, never a silent failure. */
public enum class SignerFailure {
    /** The user denied the request in the signer. */
    REJECTED,

    /** No answer in time (remote signer offline, prompt ignored). */
    TIMED_OUT,

    /** The signer app/bunker could not be reached. */
    UNREACHABLE,

    /** The signer app is not installed anymore. */
    NOT_FOUND,

    /** Not logged in / read-only. */
    NO_SIGNER,

    OTHER,
}

/** Thrown by LUMINA code paths that need a signer; carries the classified [failure]. */
public class SignerFailureException(public val failure: SignerFailure, cause: Throwable? = null) :
    Exception("Signer failure: $failure", cause)

/** Maps Quartz signer exceptions to a [SignerFailure]. */
public fun Throwable.toSignerFailure(): SignerFailure = when (this) {
    is SignerFailureException -> failure
    is SignerExceptions.ManuallyUnauthorizedException, is SignerExceptions.AutomaticallyUnauthorizedException -> SignerFailure.REJECTED
    is SignerExceptions.TimedOutException, is TimeoutCancellationException -> SignerFailure.TIMED_OUT
    is SignerExceptions.SignerNotFoundException -> SignerFailure.NOT_FOUND
    is SignerExceptions.ReadOnlyException -> SignerFailure.NO_SIGNER
    is SignerExceptions.CouldNotPerformException, is SignerExceptions.RunningOnBackgroundWithoutAutomaticPermissionException -> SignerFailure.UNREACHABLE
    else -> {
        val inner = cause
        if (inner != null && inner !== this) inner.toSignerFailure() else SignerFailure.OTHER
    }
}

/**
 * Observes every signer call: [pending] counts in-flight requests (the UI shows "Waiting for your
 * signer…" when a remote/Amber request takes a moment) and [failures] emits classified errors.
 */
@Singleton
public class SignerMonitor @Inject constructor() {
    private val _pending = MutableStateFlow(0)
    public val pending: StateFlow<Int> = _pending.asStateFlow()

    private val _failures = MutableSharedFlow<SignerFailure>(extraBufferCapacity = 8)
    public val failures: SharedFlow<SignerFailure> = _failures.asSharedFlow()

    public suspend fun <T> track(remote: Boolean, emitFailure: Boolean = true, block: suspend () -> T): T {
        _pending.update { it + 1 }
        try {
            return if (remote) withTimeout(REMOTE_TIMEOUT_MS) { block() } else block()
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                if (emitFailure) _failures.tryEmit(SignerFailure.TIMED_OUT)
                throw SignerFailureException(SignerFailure.TIMED_OUT, e)
            }
            throw e
        } catch (e: Exception) {
            val failure = e.toSignerFailure()
            if (emitFailure) _failures.tryEmit(failure)
            throw SignerFailureException(failure, e)
        } finally {
            _pending.update { (it - 1).coerceAtLeast(0) }
        }
    }

    private companion object {
        const val REMOTE_TIMEOUT_MS = 90_000L
    }
}

/**
 * Delegating [NostrSigner] that routes every call through [SignerMonitor]. Features only ever see
 * this wrapper, so they never know which signer is active.
 */
public class TrackedSigner(
    public val inner: NostrSigner,
    private val monitor: SignerMonitor,
    private val remote: Boolean,
) : NostrSigner(inner.pubKey) {
    override val pubKey: HexKey get() = inner.pubKey

    override fun isWriteable(): Boolean = inner.isWriteable()

    override suspend fun <T : Event> sign(createdAt: Long, kind: Int, tags: Array<Array<String>>, content: String): T =
        monitor.track(remote) { inner.sign(createdAt, kind, tags, content) }

    override suspend fun nip04Encrypt(plaintext: String, toPublicKey: HexKey): String =
        monitor.track(remote) { inner.nip04Encrypt(plaintext, toPublicKey) }

    override suspend fun nip04Decrypt(ciphertext: String, fromPublicKey: HexKey): String =
        monitor.track(remote, emitFailure = false) { inner.nip04Decrypt(ciphertext, fromPublicKey) }

    override suspend fun nip44Encrypt(plaintext: String, toPublicKey: HexKey): String =
        monitor.track(remote) { inner.nip44Encrypt(plaintext, toPublicKey) }

    override suspend fun nip44Decrypt(ciphertext: String, fromPublicKey: HexKey): String =
        monitor.track(remote, emitFailure = false) { inner.nip44Decrypt(ciphertext, fromPublicKey) }

    override suspend fun decryptZapEvent(event: ZapRequestEvent): PrivateZapEvent =
        monitor.track(remote, emitFailure = false) { inner.decryptZapEvent(event) }

    override suspend fun deriveKey(nonce: HexKey): HexKey = monitor.track(remote) { inner.deriveKey(nonce) }

    override suspend fun signPsbt(psbtHex: String): String = monitor.track(remote) { inner.signPsbt(psbtHex) }

    override fun hasForegroundSupport(): Boolean = inner.hasForegroundSupport()
}
