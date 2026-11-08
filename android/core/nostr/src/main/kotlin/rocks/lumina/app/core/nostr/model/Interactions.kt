package rocks.lumina.app.core.nostr.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.lightning.LnInvoiceUtil
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.UrlSafety

/** NIP-22 comment (kind 1111). */
@Immutable
public data class Comment(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val content: String,
    val rootId: String?,
    val parentId: String?,
    val parentKind: String?,
    val emoji: Map<String, String>,
    val event: Event,
) {
    /** True when this comment replies directly to the root post. */
    val isTopLevel: Boolean get() = parentId == null || parentId == rootId || parentKind == Kinds.PICTURE.toString()
}

public object CommentParser {
    public fun parse(event: Event): Comment? {
        if (event.kind != Kinds.COMMENT) return null
        return Comment(
            id = event.id,
            pubkey = event.pubKey,
            createdAt = event.createdAt,
            content = event.content,
            rootId = event.tagValue("E"),
            parentId = event.tagValue("e"),
            parentKind = event.tagValue("k"),
            emoji = event.customEmoji(),
            event = event,
        )
    }
}

/** NIP-25 reaction. */
@Immutable
public data class Reaction(
    val id: String,
    val pubkey: String,
    val createdAt: Long,
    val targetId: String?,
    val content: String,
    val emojiUrl: String?,
) {
    val isLike: Boolean get() = content != "-"
    val isPlainLike: Boolean get() = content == "+" || content.isEmpty()
}

public object ReactionParser {
    public fun parse(event: Event): Reaction? {
        if (event.kind != Kinds.REACTION) return null
        val shortcode = event.content.removeSurrounding(":")
        val emoji = event.customEmoji()[shortcode]?.takeIf { event.content.startsWith(":") }
        return Reaction(event.id, event.pubKey, event.createdAt, event.tags.lastOrNull { it.size > 1 && it[0] == "e" }?.get(1), event.content, emoji)
    }
}

/** NIP-57 zap receipt (kind 9735). Amount from the bolt11 (fallback: zap request `amount`). */
@Immutable
public data class ZapReceipt(
    val id: String,
    val recipient: String?,
    val sender: String?,
    val amountSats: Long,
    val comment: String,
    val targetId: String?,
    val createdAt: Long,
)

public object ZapParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    public fun parse(event: Event): ZapReceipt? {
        if (event.kind != Kinds.ZAP_RECEIPT) return null
        val description = event.tagValue("description")
        val request: JsonObject? = description?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val requestTags: List<List<String>> = (request?.get("tags") as? JsonArray)?.mapNotNull { t ->
            runCatching { t.jsonArray.map { it.jsonPrimitive.content } }.getOrNull()
        } ?: emptyList()
        val bolt11 = event.tagValue("bolt11")
        val fromInvoice = bolt11?.let { runCatching { LnInvoiceUtil.getAmountInSats(it).toLong() }.getOrNull() }
        val fromRequest = requestTags.firstOrNull { it.size > 1 && it[0] == "amount" }?.get(1)?.toLongOrNull()?.div(1000)
        val amount = fromInvoice?.takeIf { it > 0 } ?: fromRequest ?: 0L
        val senderFromRequest = request?.get("pubkey")?.jsonPrimitive?.contentOrNull?.takeIf(UrlSafety::isHex64)
        return ZapReceipt(
            id = event.id,
            recipient = event.tagValue("p"),
            sender = event.tagValue("P")?.takeIf(UrlSafety::isHex64) ?: senderFromRequest,
            amountSats = amount,
            comment = request?.get("content")?.jsonPrimitive?.contentOrNull.orEmpty(),
            targetId = event.tagValue("e"),
            createdAt = request?.get("created_at")?.jsonPrimitive?.longOrNull ?: event.createdAt,
        )
    }
}
