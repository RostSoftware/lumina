package rocks.lumina.app.core.nostr.model

import com.vitorpamplona.quartz.nip01Core.core.Event

/** First value of the first tag named [name]. */
public fun Event.tagValue(name: String): String? = tags.firstOrNull { it.size > 1 && it[0] == name }?.get(1)

/** All values (position 1) of tags named [name]. */
public fun Event.tagValues(name: String): List<String> = tags.filter { it.size > 1 && it[0] == name }.map { it[1] }

/** All tags named [name]. */
public fun Event.tagsNamed(name: String): List<Array<String>> = tags.filter { it.isNotEmpty() && it[0] == name }

/** Custom emoji map (NIP-30) from `emoji` tags: shortcode → https url. */
public fun Event.customEmoji(): Map<String, String> = tags
    .filter { it.size > 2 && it[0] == "emoji" }
    .mapNotNull { tag -> rocks.lumina.app.core.nostr.UrlSafety.httpsOrNull(tag[2])?.let { tag[1] to it } }
    .toMap()
