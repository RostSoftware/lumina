package rocks.lumina.app.core.nostr

import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NAddress
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer

/** A routable NIP-19 / `nostr:` reference. */
public sealed interface NostrLink {
    public data class Profile(val pubkey: String, val relays: List<String> = emptyList()) : NostrLink
    public data class Event(val id: String, val author: String? = null, val kind: Int? = null, val relays: List<String> = emptyList()) : NostrLink
    public data class Address(val kind: Int, val pubkey: String, val identifier: String, val relays: List<String> = emptyList()) : NostrLink
}

/** NIP-19 helpers built on Quartz's bech32 codecs. */
public object NostrLinks {
    public const val WEB_HOST: String = "lumina.rocks"

    public fun npub(pubkey: String): String = NPub.create(pubkey)

    public fun nprofile(pubkey: String, relays: List<String> = emptyList()): String =
        NProfile.create(pubkey, relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) })

    public fun nevent(id: String, author: String?, kind: Int? = Kinds.PICTURE, relays: List<String> = emptyList()): String =
        NEvent.create(id, author, kind, relays.mapNotNull { RelayUrlNormalizer.normalizeOrNull(it) })

    /** Shareable web link that opens in LUMINA web (and the app via App Links). */
    public fun webLink(nip19: String): String = "https://$WEB_HOST/$nip19"

    /**
     * Parses `npub…`, `nprofile…`, `note…`, `nevent…`, `naddr…`, `nostr:` URIs, `https://lumina.rocks/<nip19>`
     * and 64-char hex pubkeys. Returns null for anything else (including `nsec`, which must never be routed).
     */
    public fun parse(input: String?): NostrLink? {
        val raw = input?.trim()?.removePrefix("web+")?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.startsWith("nsec", ignoreCase = true) || raw.contains("nsec1", ignoreCase = true)) return null
        if (UrlSafety.isHex64(raw.lowercase())) return NostrLink.Profile(raw.lowercase())
        val parsed = Nip19Parser.uriToRoute(raw) ?: return null
        return when (val entity = parsed.entity) {
            is NPub -> NostrLink.Profile(entity.hex)
            is NProfile -> NostrLink.Profile(entity.hex, entity.relay.map { it.url })
            is NNote -> NostrLink.Event(entity.hex)
            is NEvent -> NostrLink.Event(entity.hex, entity.author, entity.kind, entity.relay.map { it.url })
            is NAddress -> NostrLink.Address(entity.kind, entity.author, entity.dTag, entity.relay.map { it.url })
            else -> null
        }
    }

    /** Hex pubkey from hex / npub / nprofile, or null. */
    public fun pubkeyOf(input: String?): String? = (parse(input) as? NostrLink.Profile)?.pubkey
}
