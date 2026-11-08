package rocks.lumina.app.core.nostr.model

import androidx.compose.runtime.Immutable
import com.vitorpamplona.quartz.nip01Core.core.Event
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import rocks.lumina.app.core.nostr.Kinds
import rocks.lumina.app.core.nostr.NostrNetwork
import rocks.lumina.app.core.nostr.UrlSafety

/** A NIP-65 relay entry. Both flags true when no marker is present. */
@Immutable
public data class RelayEntry(val url: String, val read: Boolean, val write: Boolean)

public object RelayListParser {
    /** kind 10002 `r` tags. */
    public fun parseNip65(event: Event): List<RelayEntry> {
        if (event.kind != Kinds.RELAY_LIST) return emptyList()
        return event.tags.filter { it.size > 1 && it[0] == "r" }.mapNotNull { tag ->
            val url = NostrNetwork.normalizeOrNull(tag[1]) ?: return@mapNotNull null
            when (tag.getOrNull(2)) {
                "read" -> RelayEntry(url, read = true, write = false)
                "write" -> RelayEntry(url, read = false, write = true)
                else -> RelayEntry(url, read = true, write = true)
            }
        }.distinctBy { it.url }
    }

    /** kind 10050 `relay` tags. */
    public fun parseDmRelays(event: Event): List<String> {
        if (event.kind != Kinds.DM_RELAYS) return emptyList()
        return event.tagValues("relay").mapNotNull(NostrNetwork::normalizeOrNull).distinct()
    }

    /** kind 10063 `server` tags (https only). */
    public fun parseBlossomServers(event: Event): List<String> {
        if (event.kind != Kinds.BLOSSOM_SERVERS) return emptyList()
        return event.tagValues("server").mapNotNull { UrlSafety.httpsOrNull(it) }.distinctBy { it.lowercase().trimEnd('/') }
    }

    public fun nip65Tags(entries: List<RelayEntry>): Array<Array<String>> = entries.mapNotNull { e ->
        when {
            e.read && e.write -> arrayOf("r", e.url)
            e.read -> arrayOf("r", e.url, "read")
            e.write -> arrayOf("r", e.url, "write")
            else -> null
        }
    }.toTypedArray()
}

/** Items of a NIP-51 mute list (public + decrypted private). */
@Immutable
public data class MuteList(
    val pubkeys: Set<String> = emptySet(),
    val words: Set<String> = emptySet(),
    val hashtags: Set<String> = emptySet(),
    val eventIds: Set<String> = emptySet(),
) {
    public fun isEmpty(): Boolean = pubkeys.isEmpty() && words.isEmpty() && hashtags.isEmpty() && eventIds.isEmpty()

    public operator fun plus(other: MuteList): MuteList = MuteList(
        pubkeys + other.pubkeys,
        words + other.words,
        hashtags + other.hashtags,
        eventIds + other.eventIds,
    )

    /** True when content by [pubkey] with [text]/[tags] must be hidden. */
    public fun hides(pubkey: String, text: String = "", tags: Collection<String> = emptyList(), eventId: String? = null): Boolean {
        if (pubkey in pubkeys) return true
        if (eventId != null && eventId in eventIds) return true
        if (tags.any { it.lowercase() in hashtags }) return true
        if (words.isNotEmpty() && text.isNotEmpty()) {
            val lower = text.lowercase()
            if (words.any { it.isNotBlank() && lower.contains(it.lowercase()) }) return true
        }
        return false
    }

    public companion object {
        private val json = Json { ignoreUnknownKeys = true }

        public fun fromTags(tags: List<List<String>>): MuteList = MuteList(
            pubkeys = tags.filter { it.size > 1 && it[0] == "p" }.map { it[1] }.filter(UrlSafety::isHex64).toSet(),
            words = tags.filter { it.size > 1 && it[0] == "word" }.map { it[1].lowercase() }.toSet(),
            hashtags = tags.filter { it.size > 1 && it[0] == "t" }.map { it[1].lowercase() }.toSet(),
            eventIds = tags.filter { it.size > 1 && it[0] == "e" }.map { it[1] }.toSet(),
        )

        /** Parses the decrypted private content (a JSON array of tags). */
        public fun parsePrivateTags(plaintext: String): List<List<String>> = runCatching {
            json.parseToJsonElement(plaintext).jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } }
        }.getOrDefault(emptyList())

        public fun privateTagsToJson(tags: List<List<String>>): String =
            kotlinx.serialization.json.JsonArray(tags.map { t -> kotlinx.serialization.json.JsonArray(t.map { kotlinx.serialization.json.JsonPrimitive(it) }) }).toString()
    }
}
