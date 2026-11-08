package rocks.lumina.app.feature.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import rocks.lumina.app.core.data.NotificationType

/** System notification channels (one per type) and the periodic polling worker. */
object NotificationsSetup {
    const val WORK_NAME: String = "lumina-notifications"
    internal const val GROUP_KEY: String = "rocks.lumina.app.NOTIFICATIONS"
    private const val CHANNEL_GROUP: String = "lumina_activity"

    internal const val CHANNEL_LIKES = "likes"
    internal const val CHANNEL_COMMENTS = "comments"
    internal const val CHANNEL_REPOSTS = "reposts"
    internal const val CHANNEL_ZAPS = "zaps"
    internal const val CHANNEL_MENTIONS = "mentions"
    internal const val CHANNEL_FOLLOWS = "follows"
    internal const val CHANNEL_MESSAGES = "messages"

    internal fun channelFor(type: NotificationType): String = when (type) {
        NotificationType.LIKE -> CHANNEL_LIKES
        NotificationType.COMMENT -> CHANNEL_COMMENTS
        NotificationType.REPOST -> CHANNEL_REPOSTS
        NotificationType.ZAP -> CHANNEL_ZAPS
        NotificationType.MENTION -> CHANNEL_MENTIONS
        NotificationType.FOLLOW -> CHANNEL_FOLLOWS
    }

    /** Creates the channels. Safe to call on every app start (no-op when unchanged). */
    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannelGroup(NotificationChannelGroup(CHANNEL_GROUP, context.getString(R.string.notifications_channel_group)))
        val channels = listOf(
            Triple(CHANNEL_MESSAGES, R.string.notifications_channel_messages, NotificationManager.IMPORTANCE_HIGH),
            Triple(CHANNEL_COMMENTS, R.string.notifications_channel_comments, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_MENTIONS, R.string.notifications_channel_mentions, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_ZAPS, R.string.notifications_channel_zaps, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CHANNEL_LIKES, R.string.notifications_channel_likes, NotificationManager.IMPORTANCE_LOW),
            Triple(CHANNEL_REPOSTS, R.string.notifications_channel_reposts, NotificationManager.IMPORTANCE_LOW),
            Triple(CHANNEL_FOLLOWS, R.string.notifications_channel_follows, NotificationManager.IMPORTANCE_LOW),
        ).map { (id, name, importance) ->
            NotificationChannel(id, context.getString(name), importance).apply { group = CHANNEL_GROUP }
        }
        manager.createNotificationChannels(channels)
    }

    /** Schedules the 15-minute polling worker (unique; keeps an existing schedule). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Stops polling (e.g. when the last account logs out). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    internal fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
