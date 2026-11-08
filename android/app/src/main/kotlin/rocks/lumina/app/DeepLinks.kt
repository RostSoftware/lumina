package rocks.lumina.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrLink
import rocks.lumina.app.core.nostr.NostrLinks
import rocks.lumina.app.feature.notifications.NotificationWorker
import rocks.lumina.app.navigation.Route

/** What an incoming Intent asks for. */
sealed interface IncomingIntent {
    data class Open(val route: Route) : IncomingIntent
    data class Share(val uris: List<Uri>) : IncomingIntent
    data object Unsupported : IncomingIntent
}

object DeepLinks {
    /** Maps NIP-19 / `nostr:` / `https://lumina.rocks/<nip19>` to a route. */
    fun routeFor(link: NostrLink): Route? = when (link) {
        is NostrLink.Profile -> Route.Profile(link.pubkey)
        is NostrLink.Event -> if (link.kind == null || link.kind == Kinds.PICTURE) Route.Post(link.id, link.relays, link.author) else null
        is NostrLink.Address -> null
    }

    fun routeFor(raw: String?): Route? {
        val value = raw?.trim() ?: return null
        val path = if (value.startsWith("https://", ignoreCase = true)) {
            val uri = Uri.parse(value)
            if (uri.host?.lowercase() != NostrLinks.WEB_HOST) return null
            val segments = uri.pathSegments
            when {
                segments.isEmpty() -> return Route.Home
                segments.first() == "discover" -> return Route.Discover(uri.getQueryParameter("t"))
                segments.first() == "messages" && segments.size > 1 -> return NostrLinks.pubkeyOf(segments[1])?.let { Route.Chat(it) }
                else -> segments.last()
            }
        } else {
            value
        }
        return NostrLinks.parse(path)?.let(::routeFor)
    }

    fun parse(intent: Intent?): IncomingIntent? {
        intent ?: return null
        when (intent.getStringExtra(NotificationWorker.EXTRA_OPEN)) {
            NotificationWorker.OPEN_MESSAGES -> return IncomingIntent.Open(Route.Messages)
            NotificationWorker.OPEN_NOTIFICATIONS -> return IncomingIntent.Open(Route.Notifications)
        }
        return when (intent.action) {
            Intent.ACTION_VIEW -> routeFor(intent.dataString)?.let { IncomingIntent.Open(it) } ?: IncomingIntent.Unsupported
            Intent.ACTION_SEND -> intent.streamUri()?.takeIf(::isContentUri)?.let { IncomingIntent.Share(listOf(it)) }
            Intent.ACTION_SEND_MULTIPLE -> intent.streamUris().filter(::isContentUri).take(10).takeIf { it.isNotEmpty() }?.let { IncomingIntent.Share(it) }
            else -> null
        }
    }

    /** Only content:// URIs are accepted from other apps (file:// could point at our private files). */
    private fun isContentUri(uri: Uri): Boolean = uri.scheme == "content"

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): Uri? =
        if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

    @Suppress("DEPRECATION")
    private fun Intent.streamUris(): List<Uri> =
        (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableArrayListExtra(Intent.EXTRA_STREAM))
            ?.toList().orEmpty()
}
