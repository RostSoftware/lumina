package rocks.lumina.app.core.nostr.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.UrlSafety

/** Kind 0 metadata (sanitised). [raw] keeps every field so edits never drop unknown keys. */
@Immutable
public data class Profile(
    val pubkey: String,
    val name: String?,
    val displayName: String?,
    val about: String?,
    val picture: String?,
    val banner: String?,
    val website: String?,
    val nip05: String?,
    val lud16: String?,
    val lud06: String?,
    val createdAt: Long,
    val raw: JsonObject,
) {
    val bestName: String get() = displayName?.takeIf { it.isNotBlank() } ?: name?.takeIf { it.isNotBlank() } ?: shortNpub(pubkey)
    val handle: String? get() = name?.takeIf { it.isNotBlank() && it != displayName }
    val lightningAddress: String? get() = lud16?.takeIf { it.isNotBlank() } ?: lud06?.takeIf { it.isNotBlank() }

    public companion object {
        public fun empty(pubkey: String): Profile = Profile(pubkey, null, null, null, null, null, null, null, null, null, 0, JsonObject(emptyMap()))

        public fun shortNpub(pubkey: String): String = runCatching {
            val npub = NPub.create(pubkey)
            npub.take(10) + "…" + npub.takeLast(4)
        }.getOrDefault(pubkey.take(8))
    }
}

public object ProfileParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    public fun parse(event: Event): Profile? {
        if (event.kind != Kinds.METADATA) return null
        val obj = runCatching { json.parseToJsonElement(event.content).jsonObject }.getOrNull() ?: return null
        fun str(key: String): String? = (obj[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return Profile(
            pubkey = event.pubKey,
            name = str("name") ?: str("username"),
            displayName = str("display_name") ?: str("displayName"),
            about = str("about"),
            picture = UrlSafety.httpsOrNull(str("picture")),
            banner = UrlSafety.httpsOrNull(str("banner")),
            website = UrlSafety.websiteOrNull(str("website")),
            nip05 = str("nip05"),
            lud16 = str("lud16"),
            lud06 = str("lud06"),
            createdAt = event.createdAt,
            raw = obj,
        )
    }
}
