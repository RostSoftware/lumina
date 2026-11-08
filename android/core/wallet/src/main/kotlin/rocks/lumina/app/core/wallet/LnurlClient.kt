package rocks.lumina.app.core.wallet

import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.lightning.Lud06
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import rocks.lumina.app.core.nostr.UrlSafety

/** LUD-06 pay parameters for a lightning address. */
public data class LnurlPayInfo(
    val callback: String,
    val minSendableMsat: Long,
    val maxSendableMsat: Long,
    val allowsNostr: Boolean,
    val nostrPubkey: String?,
    val lnurl: String,
    val commentAllowed: Int,
)

public class LnurlException(message: String) : Exception(message)

/** LNURL-pay over HTTP (Quartz gap #3): `lud16` → `.well-known/lnurlp`, then the callback with a zap request. */
@Singleton
public class LnurlClient @Inject constructor(
    private val okHttp: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Resolves a `name@domain` lightning address (lud16) or an `lnurl1…` (lud06). */
    public suspend fun resolve(address: String): LnurlPayInfo = withContext(Dispatchers.IO) {
        val trimmed = address.trim()
        val url = when {
            trimmed.contains('@') -> {
                val (name, domain) = trimmed.split('@', limit = 2)
                if (name.isBlank() || domain.isBlank() || domain.contains('/')) throw LnurlException("Invalid lightning address")
                "https://$domain/.well-known/lnurlp/${name.lowercase()}"
            }
            trimmed.startsWith("lnurl", ignoreCase = true) -> Lud06().toLnUrlp(trimmed) ?: throw LnurlException("Invalid LNURL")
            else -> throw LnurlException("Invalid lightning address")
        }
        val safe = UrlSafety.httpsOrNull(url) ?: throw LnurlException("LNURL must use https")
        val obj = getJson(safe)
        if ((obj["status"] as? JsonPrimitive)?.content == "ERROR") throw LnurlException((obj["reason"] as? JsonPrimitive)?.content ?: "LNURL error")
        val callback = UrlSafety.httpsOrNull((obj["callback"] as? JsonPrimitive)?.content) ?: throw LnurlException("LNURL has no callback")
        LnurlPayInfo(
            callback = callback,
            minSendableMsat = (obj["minSendable"] as? JsonPrimitive)?.longOrNull ?: 1000,
            maxSendableMsat = (obj["maxSendable"] as? JsonPrimitive)?.longOrNull ?: Long.MAX_VALUE,
            allowsNostr = (obj["allowsNostr"] as? JsonPrimitive)?.booleanOrNull ?: false,
            nostrPubkey = (obj["nostrPubkey"] as? JsonPrimitive)?.content?.lowercase()?.takeIf(UrlSafety::isHex64),
            lnurl = trimmed,
            commentAllowed = (obj["commentAllowed"] as? JsonPrimitive)?.longOrNull?.toInt() ?: 0,
        )
    }

    /**
     * Requests an invoice for [amountMsat]. When [zapRequestJson] is given (NIP-57) it is sent as `nostr=`.
     * The returned bolt11 amount is validated against the requested amount.
     */
    public suspend fun fetchInvoice(info: LnurlPayInfo, amountMsat: Long, zapRequestJson: String?, comment: String?): String = withContext(Dispatchers.IO) {
        if (amountMsat < info.minSendableMsat || amountMsat > info.maxSendableMsat) {
            throw LnurlException("Amount must be between ${info.minSendableMsat / 1000} and ${info.maxSendableMsat / 1000} sats")
        }
        val separator = if (info.callback.contains('?')) "&" else "?"
        val sb = StringBuilder(info.callback).append(separator).append("amount=").append(amountMsat)
        if (zapRequestJson != null) sb.append("&nostr=").append(URLEncoder.encode(zapRequestJson, "UTF-8"))
        if (!comment.isNullOrBlank() && zapRequestJson == null && info.commentAllowed > 0) {
            sb.append("&comment=").append(URLEncoder.encode(comment.take(info.commentAllowed), "UTF-8"))
        }
        if (info.lnurl.startsWith("lnurl", ignoreCase = true)) sb.append("&lnurl=").append(info.lnurl)
        val obj = getJson(sb.toString())
        if ((obj["status"] as? JsonPrimitive)?.content == "ERROR") throw LnurlException((obj["reason"] as? JsonPrimitive)?.content ?: "Invoice error")
        val pr = (obj["pr"] as? JsonPrimitive)?.content ?: throw LnurlException("No invoice returned")
        val sats = runCatching { LnInvoiceUtil.getAmountInSats(pr).toLong() }.getOrNull()
        if (sats != null && sats * 1000 != amountMsat && sats != amountMsat / 1000) throw LnurlException("Invoice amount mismatch")
        pr
    }

    private fun getJson(url: String): JsonObject {
        val request = Request.Builder().url(url).get().header("Accept", "application/json").build()
        okHttp.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) throw LnurlException("HTTP ${response.code}")
            return runCatching { json.parseToJsonElement(body).jsonObject }.getOrElse { throw LnurlException("Invalid LNURL response") }
        }
    }
}
