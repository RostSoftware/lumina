package rocks.lumina.app.feature.settings

import rocks.lumina.app.core.nostr.UrlSafety
import rocks.lumina.app.core.nostr.model.RelayEntry

/** Why an entry couldn't be added to a list. */
enum class AddError { INVALID, DUPLICATE }

/** Result of adding to an editable list: the new list or an error. */
data class AddResult<T>(val list: List<T>, val error: AddError? = null)

/** Pure list editing for relay/server screens (unit tested; the normalizer is injected for tests). */
object ListEditing {
    fun key(url: String): String = url.trim().lowercase().trimEnd('/')

    fun addRelay(list: List<RelayEntry>, input: String, normalize: (String) -> String?): AddResult<RelayEntry> {
        val url = relayUrl(input, normalize) ?: return AddResult(list, AddError.INVALID)
        if (list.any { key(it.url) == key(url) }) return AddResult(list, AddError.DUPLICATE)
        return AddResult(list + RelayEntry(url, read = true, write = true))
    }

    fun addDmRelay(list: List<String>, input: String, normalize: (String) -> String?): AddResult<String> {
        val url = relayUrl(input, normalize) ?: return AddResult(list, AddError.INVALID)
        if (list.any { key(it) == key(url) }) return AddResult(list, AddError.DUPLICATE)
        return AddResult(list + url)
    }

    /** Accepts `relay.example.com` (upgraded to wss://) and ws(s):// URLs. */
    fun relayUrl(input: String, normalize: (String) -> String?): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "wss://$trimmed"
        if (!withScheme.startsWith("wss://", true) && !withScheme.startsWith("ws://", true)) return null
        val host = withScheme.substringAfter("://").substringBefore('/').substringBefore(':')
        if (!host.contains('.') && host != "localhost") return null
        return normalize(withScheme)
    }

    /**
     * Toggles read or write. A relay must keep at least one marker, so turning off the last one is
     * ignored (remove the relay instead).
     */
    fun toggle(list: List<RelayEntry>, url: String, read: Boolean): List<RelayEntry> = list.map { e ->
        if (e.url != url) {
            e
        } else {
            val next = if (read) e.copy(read = !e.read) else e.copy(write = !e.write)
            if (!next.read && !next.write) e else next
        }
    }

    fun hasWriteRelay(list: List<RelayEntry>): Boolean = list.any { it.write }

    /** Blossom servers must be https. Bare hosts are upgraded to https://. */
    fun serverUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.contains("://") && !trimmed.startsWith("https://", true)) return null
        val withScheme = if (trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        val url = UrlSafety.httpsOrNull(withScheme) ?: return null
        val host = url.substringAfter("://").substringBefore('/').substringBefore(':')
        if (!host.contains('.')) return null
        return if (url.endsWith('/')) url else "$url/"
    }

    fun addServer(list: List<String>, input: String): AddResult<String> {
        val url = serverUrl(input) ?: return AddResult(list, AddError.INVALID)
        if (list.any { key(it) == key(url) }) return AddResult(list, AddError.DUPLICATE)
        return AddResult(list + url)
    }

    fun <T> move(list: List<T>, index: Int, delta: Int): List<T> {
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return list
        return list.toMutableList().apply { add(target, removeAt(index)) }
    }

    /** Same relays/flags regardless of URL spelling. */
    fun sameRelays(a: List<RelayEntry>, b: List<RelayEntry>): Boolean =
        a.map { Triple(key(it.url), it.read, it.write) } == b.map { Triple(key(it.url), it.read, it.write) }

    fun sameUrls(a: List<String>, b: List<String>): Boolean = a.map(::key) == b.map(::key)
}

object ZapAmount {
    const val MAX_SATS: Long = 10_000_000

    fun parse(input: String): Long? {
        val digits = input.trim().replace(",", "").replace(".", "").replace(" ", "")
        val value = digits.toLongOrNull() ?: return null
        return value.takeIf { it in 1..MAX_SATS }
    }
}

object WalletInput {
    fun looksLikeNwc(input: String): Boolean {
        val t = input.trim()
        return (t.startsWith("nostr+walletconnect://", true) || t.startsWith("nostrwalletconnect://", true)) &&
            t.contains("relay=") && t.contains("secret=")
    }
}
