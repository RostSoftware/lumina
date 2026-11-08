package rocks.lumina.app.feature.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.utils.TimeUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import rocks.lumina.app.core.data.NotificationGroup
import rocks.lumina.app.core.data.NotificationRepository
import rocks.lumina.app.core.data.NotificationType
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.model.Profile
import rocks.lumina.app.core.signer.AccountManager
import rocks.lumina.app.core.ui.util.formatSats

/**
 * Polls relays every 15 minutes for the active account and posts system notifications per type
 * (likes, comments, reposts, zaps, mentions, follows) plus a content-free "New message" notification
 * when new NIP-17 gift wraps arrive. Nothing is decrypted here.
 */
@HiltWorker
class NotificationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountManager: AccountManager,
    private val settings: SettingsRepository,
    private val notifications: NotificationRepository,
    private val relays: RelayRepository,
    private val network: NostrNetwork,
    private val profiles: ProfileRepository,
) : CoroutineWorker(context, params) {

    private val prefs by lazy { applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override suspend fun doWork(): Result {
        withTimeoutOrNull(10_000) { accountManager.awaitReady() }
        pruneRemovedAccounts()
        val me = accountManager.activePubkey ?: return Result.success()
        if (!settings.settingsFor(me).systemNotifications) return Result.success()
        val now = TimeUtils.now()
        val last = settings.lastSystemNotified(me)
        val firstRun = last <= 0L
        val since = if (firstRun) now - FIRST_RUN_WINDOW else last
        if (!NotificationsSetup.canNotify(applicationContext)) {
            // Don't pile up a backlog to flood the user with once permission is granted.
            settings.setLastSystemNotified(me, now)
            return Result.success()
        }
        NotificationsSetup.createChannels(applicationContext)
        runCatching { relays.loadOwn(me) }

        val raw = runCatching { notifications.fetchRaw(me, since) }.getOrElse {
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
        val fresh = raw.filter { it.createdAt > since && it.createdAt <= now + CLOCK_SKEW }
        val groups = notifications.group(fresh, lastSeen = 0).take(MAX_NOTIFICATIONS)
        val names = resolveNames(groups.flatMap { it.actors.take(2) }.distinct())
        groups.forEach { post(it, me, names) }

        val newMessages = runCatching { newGiftWraps(me, firstRun) }.getOrDefault(0)
        if (newMessages > 0) postMessage(newMessages)

        if (groups.isNotEmpty() || newMessages > 0) postSummary()
        settings.setLastSystemNotified(me, now)
        return Result.success()
    }

    private suspend fun resolveNames(pubkeys: List<String>): Map<String, String> = coroutineScope {
        pubkeys.take(MAX_PROFILE_LOOKUPS).map { pk ->
            async {
                val profile = profiles.peek(pk) ?: withTimeoutOrNull(5_000) { runCatching { profiles.refresh(pk) }.getOrNull() }
                pk to (profile?.bestName ?: Profile.shortNpub(pk))
            }
        }.awaitAll().toMap()
    }

    /** Counts gift wraps addressed to [me] that neither the inbox nor a previous run has seen. */
    private suspend fun newGiftWraps(me: String, firstRun: Boolean): Int {
        // NIP-59 randomizes created_at up to two days into the past, so look back further and dedupe by id.
        val filter = Filter(
            kinds = listOf(Kinds.GIFT_WRAP),
            tags = mapOf("p" to listOf(me)),
            since = TimeUtils.now() - GIFT_WRAP_LOOKBACK,
            limit = 200,
        )
        val ids = network.query(relays.ownDmRelays(), listOf(filter)).map { it.id }.toSet()
        if (ids.isEmpty()) return 0
        val notified = prefs.getStringSet(wrapsKey(me), emptySet()).orEmpty()
        val seen = settings.seenWrapIds(me)
        val unseen = ids - notified - seen
        val remembered = (notified + ids).let { if (it.size > MAX_REMEMBERED_WRAPS) ids else it }
        prefs.edit { putStringSet(wrapsKey(me), remembered) }
        return if (firstRun) 0 else unseen.size
    }

    private fun pruneRemovedAccounts() {
        val known = accountManager.accounts.value.map { it.pubkey }.toSet()
        val stale = prefs.all.keys.filter { key -> key.startsWith(WRAPS_PREFIX) && key.removePrefix(WRAPS_PREFIX) !in known }
        if (stale.isNotEmpty()) prefs.edit { stale.forEach { remove(it) } }
    }

    @SuppressLint("MissingPermission") // checked in NotificationsSetup.canNotify before posting
    private fun post(group: NotificationGroup, me: String, names: Map<String, String>) {
        val context = applicationContext
        val res = context.resources
        val first = group.actors.firstOrNull() ?: return
        val firstName = names[first] ?: Profile.shortNpub(first)
        val others = group.actors.size - 1
        val actors = when {
            others == 0 -> firstName
            others == 1 -> context.getString(R.string.notifications_actors_two, firstName, names[group.actors[1]] ?: Profile.shortNpub(group.actors[1]))
            else -> res.getQuantityString(R.plurals.notifications_actors_others, others, firstName, others)
        }
        val text = when (group.type) {
            NotificationType.LIKE -> context.getString(R.string.notifications_liked, actors)
            NotificationType.COMMENT -> context.getString(R.string.notifications_commented, actors, group.text.orEmpty().take(200))
            NotificationType.REPOST -> context.getString(R.string.notifications_reposted, actors)
            NotificationType.ZAP -> res.getQuantityString(
                R.plurals.notifications_zapped,
                group.zapSats.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                actors,
                formatSats(group.zapSats),
            )
            NotificationType.MENTION -> context.getString(R.string.notifications_mentioned, actors)
            NotificationType.FOLLOW -> context.getString(R.string.notifications_followed, actors)
        }
        val uri = NotificationTargets.uriFor(group.type, group.postId, first, me)
        val id = group.key.hashCode()
        val notification = NotificationCompat.Builder(context, NotificationsSetup.channelFor(group.type))
            .setSmallIcon(R.drawable.notifications_ic_stat)
            .setContentTitle(context.getString(R.string.notifications_app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setWhen(group.createdAt * 1000)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setGroup(NotificationsSetup.GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setContentIntent(openIntent(uri.toUri(), id))
            .build()
        NotificationManagerCompat.from(context).notify(TAG, id, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postMessage(count: Int) {
        val context = applicationContext
        val text = context.resources.getQuantityString(R.plurals.notifications_new_messages, count, count)
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.packageName, MAIN_ACTIVITY)
            putExtra(EXTRA_OPEN, OPEN_MESSAGES)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, MESSAGES_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationsSetup.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.notifications_ic_stat)
            .setContentTitle(context.getString(R.string.notifications_new_message_title))
            .setContentText(text)
            .setNumber(count)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup(NotificationsSetup.GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(TAG, MESSAGES_ID, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postSummary() {
        val context = applicationContext
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setClassName(context.packageName, MAIN_ACTIVITY)
            putExtra(EXTRA_OPEN, OPEN_NOTIFICATIONS)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, SUMMARY_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val summary = NotificationCompat.Builder(context, NotificationsSetup.CHANNEL_LIKES)
            .setSmallIcon(R.drawable.notifications_ic_stat)
            .setContentTitle(context.getString(R.string.notifications_app_name))
            .setContentText(context.getString(R.string.notifications_summary))
            .setGroup(NotificationsSetup.GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(TAG, SUMMARY_ID, summary)
    }

    private fun openIntent(uri: Uri, requestCode: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClassName(applicationContext.packageName, MAIN_ACTIVITY)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(applicationContext, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val MAIN_ACTIVITY: String = "rocks.lumina.app.MainActivity"

        /** Intent extra telling MainActivity which screen to open for non-deep-link notifications. */
        const val EXTRA_OPEN: String = "rocks.lumina.app.extra.OPEN"
        const val OPEN_MESSAGES: String = "messages"
        const val OPEN_NOTIFICATIONS: String = "notifications"

        private const val TAG = "lumina"
        private const val PREFS = "lumina_notification_worker"
        private const val WRAPS_PREFIX = "wraps:"
        private fun wrapsKey(pubkey: String) = WRAPS_PREFIX + pubkey

        private const val MESSAGES_ID = 1_059
        private const val SUMMARY_ID = 1
        private const val MAX_NOTIFICATIONS = 10
        private const val MAX_PROFILE_LOOKUPS = 20
        private const val MAX_ATTEMPTS = 3
        private const val FIRST_RUN_WINDOW = 3_600L
        private const val CLOCK_SKEW = 600L
        private const val GIFT_WRAP_LOOKBACK = 3 * 86_400L
        private const val MAX_REMEMBERED_WRAPS = 1_000
    }
}
