package rocks.lumina.app.core.nostr

import rocks.lumina.app.core.nostr.model.RelayEntry

/** App defaults — identical to `lumina-web/src/lib/appRelays.ts` and `appBlossom.ts`. */
public object RelayDefaults {
    public val appRelays: List<RelayEntry> = listOf(
        RelayEntry("wss://relay.ditto.pub/", read = true, write = true),
        RelayEntry("wss://relay.dreamith.to/", read = true, write = true),
        RelayEntry("wss://relay.primal.net/", read = false, write = true),
        RelayEntry("wss://nos.lol/", read = false, write = true),
    )

    /** Relays that index kind 0 / 3 / 10002 widely; used to discover other users' relay lists. */
    public val indexerRelays: List<String> = listOf(
        "wss://purplepag.es/",
        "wss://user.kindpag.es/",
        "wss://relay.damus.io/",
        "wss://relay.primal.net/",
    )

    /** NIP-50 search relays (user search, hashtag search fallback). */
    public val searchRelays: List<String> = listOf(
        "wss://relay.ditto.pub/",
        "wss://relay.nostr.band/",
        "wss://search.nos.today/",
    )

    public val blossomServers: List<String> = listOf(
        "https://blossom.ditto.pub/",
        "https://blossom.dreamith.to/",
        "https://blossom.primal.net/",
    )

    /** Preset zap amounts in sats (web: `ZAP_PRESETS`). */
    public val zapPresets: List<Long> = listOf(21, 100, 500, 1000, 5000, 10000)
}
