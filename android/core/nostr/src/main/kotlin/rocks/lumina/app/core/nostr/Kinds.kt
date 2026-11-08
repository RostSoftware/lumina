package rocks.lumina.app.core.nostr

/** Nostr event kinds used by LUMINA. No app-private kinds exist (see root AGENTS.md). */
public object Kinds {
    public const val METADATA: Int = 0
    public const val CONTACTS: Int = 3
    public const val LEGACY_DM: Int = 4
    public const val DELETION: Int = 5
    public const val REPOST: Int = 6
    public const val REACTION: Int = 7
    public const val SEAL: Int = 13
    public const val CHAT_MESSAGE: Int = 14
    public const val GENERIC_REPOST: Int = 16
    public const val PICTURE: Int = 20
    public const val GIFT_WRAP: Int = 1059
    public const val COMMENT: Int = 1111
    public const val REPORT: Int = 1984
    public const val ZAP_REQUEST: Int = 9734
    public const val ZAP_RECEIPT: Int = 9735
    public const val MUTE_LIST: Int = 10000
    public const val RELAY_LIST: Int = 10002
    public const val BOOKMARKS: Int = 10003
    public const val DM_RELAYS: Int = 10050
    public const val BLOSSOM_SERVERS: Int = 10063
    public const val BLOSSOM_AUTH: Int = 24242
}
