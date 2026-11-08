package rocks.lumina.app.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rocks.lumina.app.core.nostr.NostrNetwork

/**
 * Publishes an already-signed event in WorkManager so a new post survives process death and flaky
 * networks (retries with exponential backoff). Signing happens in the foreground beforehand because
 * remote signers may need user approval.
 */
@HiltWorker
public class PublishWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val network: NostrNetwork,
    private val cache: EventCache,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val path = inputData.getString(KEY_EVENT_FILE) ?: return Result.failure()
        val file = File(path)
        val json = runCatching { file.readText() }.getOrNull() ?: return Result.failure()
        val event = Event.fromJsonOrNull(json)?.takeIf { it.verify() } ?: return Result.failure()
        val relays = inputData.getStringArray(KEY_RELAYS)?.toList().orEmpty()
        val accepted = network.publish(event, relays, timeoutSeconds = 15)
        return if (accepted.isNotEmpty()) {
            cache.insert(event)
            file.delete()
            Result.success(workDataOf(KEY_ACCEPTED to accepted.size))
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            file.delete()
            Result.failure()
        }
    }

    public companion object {
        public const val KEY_EVENT_FILE: String = "eventFile"
        public const val KEY_RELAYS: String = "relays"
        public const val KEY_ACCEPTED: String = "accepted"
        private const val MAX_ATTEMPTS = 6
    }
}

public enum class PublishJobState { QUEUED, RUNNING, SUCCEEDED, FAILED }

/** Enqueues [PublishWorker] jobs and observes their state. */
@Singleton
public class PublishScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val relays: RelayRepository,
) {
    public suspend fun enqueue(event: Event): UUID {
        val tagged = event.tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }
        val targets = (relays.writeRelays() + relays.inboxRelaysOf(tagged)).distinct()
        // The signed event is written to app-private storage (it is public data) to avoid Data size limits.
        val dir = File(context.filesDir, "outbox").apply { mkdirs() }
        val file = File(dir, "${event.id}.json").apply { writeText(event.toJson()) }
        val request = OneTimeWorkRequestBuilder<PublishWorker>()
            .setInputData(workDataOf(PublishWorker.KEY_EVENT_FILE to file.absolutePath, PublishWorker.KEY_RELAYS to targets.toTypedArray()))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return request.id
    }

    public fun observe(id: UUID): Flow<PublishJobState> = WorkManager.getInstance(context).getWorkInfoByIdFlow(id).map { info ->
        when (info?.state) {
            WorkInfo.State.SUCCEEDED -> PublishJobState.SUCCEEDED
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> PublishJobState.FAILED
            WorkInfo.State.RUNNING -> PublishJobState.RUNNING
            else -> PublishJobState.QUEUED
        }
    }

    private companion object {
        const val TAG = "lumina-publish"
    }
}
