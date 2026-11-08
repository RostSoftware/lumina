package rocks.lumina.app.core.signer

import com.vitorpamplona.quartz.nip01Core.signers.SignerExceptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SignerFailureTest {
    @Test
    fun quartzExceptionsAreClassified() {
        assertEquals(SignerFailure.REJECTED, SignerExceptions.ManuallyUnauthorizedException("no").toSignerFailure())
        assertEquals(SignerFailure.REJECTED, SignerExceptions.AutomaticallyUnauthorizedException("no").toSignerFailure())
        assertEquals(SignerFailure.TIMED_OUT, SignerExceptions.TimedOutException("slow").toSignerFailure())
        assertEquals(SignerFailure.UNREACHABLE, SignerExceptions.CouldNotPerformException("down").toSignerFailure())
        assertEquals(SignerFailure.NOT_FOUND, SignerExceptions.SignerNotFoundException("gone").toSignerFailure())
        assertEquals(SignerFailure.OTHER, IllegalStateException("x").toSignerFailure())
        assertEquals(SignerFailure.REJECTED, RuntimeException("wrap", SignerExceptions.ManuallyUnauthorizedException("no")).toSignerFailure())
    }

    @Test
    fun monitorTracksPendingAndMapsFailures() = runTest {
        val monitor = SignerMonitor()
        try {
            monitor.track(remote = false) {
                assertEquals(1, monitor.pending.value)
                throw SignerExceptions.ManuallyUnauthorizedException("denied")
            }
            fail("expected failure")
        } catch (e: SignerFailureException) {
            assertEquals(SignerFailure.REJECTED, e.failure)
        }
        assertEquals(0, monitor.pending.value)
    }
}
