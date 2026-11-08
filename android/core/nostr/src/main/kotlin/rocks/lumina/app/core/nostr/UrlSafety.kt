package rocks.lumina.app.core.nostr

/** Event fields are untrusted: only `https` URLs are ever loaded or linked. */
public object UrlSafety {
    private val httpsRegex = Regex("^https://[^\\s<>\"]+$", RegexOption.IGNORE_CASE)

    public fun httpsOrNull(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        return if (httpsRegex.matches(trimmed)) trimmed else null
    }

    /** Websites in profiles are often written without a scheme; upgrade bare hosts to https. */
    public fun websiteOrNull(url: String?): String? {
        val trimmed = url?.trim() ?: return null
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("https://", ignoreCase = true)) return httpsOrNull(trimmed)
        if (trimmed.startsWith("http://", ignoreCase = true)) return httpsOrNull("https://" + trimmed.substring(7))
        if (trimmed.contains("://")) return null
        if (!trimmed.contains('.')) return null
        return httpsOrNull("https://$trimmed")
    }

    public fun isHex64(value: String?): Boolean = value != null && value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
