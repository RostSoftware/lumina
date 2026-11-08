package rocks.lumina.app.core.media

import com.vitorpamplona.quartz.nip01Core.signers.NostrSigner
import com.vitorpamplona.quartz.nipB7Blossom.BlossomAuthorizationEvent
import com.vitorpamplona.quartz.utils.TimeUtils
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import rocks.lumina.app.core.data.RelayRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.nostr.RelayDefaults
import rocks.lumina.app.core.nostr.UrlSafety

/** A finished Blossom upload. */
public data class BlossomBlob(val url: String, val sha256: String, val size: Long, val mimeType: String, val server: String)

public class BlossomUploadException(message: String, public val perServer: Map<String, String>) : Exception(message)

/**
 * Blossom HTTP client (Quartz gap #1): BUD-02 `PUT /upload` with a kind 24242 `Authorization: Nostr …`
 * header (from Quartz's `BlossomAuthorizationEvent`), progress callbacks and server-list failover.
 */
@Singleton
public class BlossomClient @Inject constructor(
    private val okHttp: OkHttpClient,
    private val relays: RelayRepository,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** User's kind 10063 servers, merged with the app defaults when that toggle is on (web parity). */
    public fun servers(): List<String> {
        val user = relays.blossomServers.value
        val merged = if (settings.settings.value.useDefaultBlossomServers || user.isEmpty()) user + RelayDefaults.blossomServers else user
        return merged.distinctBy { it.lowercase().trimEnd('/') }
    }

    public suspend fun upload(
        image: PreparedImage,
        signer: NostrSigner,
        onProgress: (Float) -> Unit = {},
    ): BlossomBlob = withContext(Dispatchers.IO) {
        val servers = servers()
        val errors = LinkedHashMap<String, String>()
        val auth = BlossomAuthorizationEvent.createUploadAuth(
            hash = image.sha256,
            size = image.size,
            alt = "Upload ${image.mimeType}",
            signer = signer,
            expiration = TimeUtils.now() + 600,
        )
        for (server in servers) {
            try {
                return@withContext uploadTo(server, image, auth.toAuthorizationHeader(), onProgress)
            } catch (e: Exception) {
                errors[server] = e.message ?: e.javaClass.simpleName
                onProgress(0f)
            }
        }
        throw BlossomUploadException("Upload failed on all media servers", errors)
    }

    private fun uploadTo(server: String, image: PreparedImage, authHeader: String, onProgress: (Float) -> Unit): BlossomBlob {
        val base = server.trimEnd('/')
        val body = ProgressBody(image.bytes, image.mimeType.toMediaType(), onProgress)
        val request = Request.Builder()
            .url("$base/upload")
            .put(body)
            .header("Authorization", authHeader)
            .header("Content-Type", image.mimeType)
            .header("X-SHA-256", image.sha256)
            .build()
        okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                val reason = response.header("X-Reason") ?: text.take(120)
                throw IllegalStateException("HTTP ${response.code}: $reason")
            }
            val obj = json.parseToJsonElement(text).jsonObject
            fun str(key: String) = (obj[key] as? JsonPrimitive)?.content
            val url = UrlSafety.httpsOrNull(str("url")) ?: throw IllegalStateException("server returned no https url")
            val hash = str("sha256")?.lowercase()
            if (hash != null && hash != image.sha256) throw IllegalStateException("hash mismatch")
            onProgress(1f)
            return BlossomBlob(url, image.sha256, str("size")?.toLongOrNull() ?: image.size, str("type") ?: image.mimeType, base)
        }
    }

    /** Reachability test for the media-server settings screen. Returns round-trip ms or null. */
    public suspend fun test(server: String): Long? = withContext(Dispatchers.IO) {
        val url = UrlSafety.httpsOrNull(server.trim()) ?: return@withContext null
        runCatching {
            val start = System.currentTimeMillis()
            okHttp.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                if (response.code >= 500) null else System.currentTimeMillis() - start
            }
        }.getOrNull()
    }

    /** Candidate fallback URLs for a blob (`<server>/<sha256>.<ext>`) on other known servers. */
    public fun fallbackUrls(sha256: String, originalUrl: String): List<String> {
        val ext = originalUrl.substringAfterLast('/').substringAfterLast('.', "").substringBefore('?').takeIf { it.length in 2..5 }
        val suffix = if (ext != null) ".$ext" else ""
        val originalHost = originalUrl.substringAfter("://").substringBefore('/')
        return servers().map { it.trimEnd('/') }
            .filterNot { it.substringAfter("://").substringBefore('/') == originalHost }
            .map { "$it/$sha256$suffix" }
    }

    private class ProgressBody(
        private val bytes: ByteArray,
        private val type: MediaType,
        private val onProgress: (Float) -> Unit,
    ) : RequestBody() {
        override fun contentType(): MediaType = type
        override fun contentLength(): Long = bytes.size.toLong()
        override fun writeTo(sink: BufferedSink) {
            val total = bytes.size
            var written = 0
            val chunk = 16 * 1024
            while (written < total) {
                val count = minOf(chunk, total - written)
                sink.write(bytes, written, count)
                written += count
                onProgress((written.toFloat() / total).coerceAtMost(0.99f))
            }
        }
    }

    @Suppress("unused")
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.content
}
