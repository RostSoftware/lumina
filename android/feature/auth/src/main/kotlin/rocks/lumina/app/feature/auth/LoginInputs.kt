package rocks.lumina.app.feature.auth

import com.vitorpamplona.quartz.nip55AndroidSigner.api.CommandType
import com.vitorpamplona.quartz.nip55AndroidSigner.api.permission.Permission
import rocks.lumina.app.core.signer.Nip46Permissions

/** Pure input checks for the login flow (unit tested). */
object LoginInputs {
    private val hex64 = Regex("^[0-9a-fA-F]{64}$")
    private val nsec = Regex("^nsec1[02-9ac-hj-np-z]{58}$")

    /** A `bunker://<64-hex>?relay=…` link. The full parse happens in AccountManager. */
    fun isBunkerUri(input: String): Boolean {
        val trimmed = input.trim()
        if (!trimmed.startsWith("bunker://", ignoreCase = true)) return false
        val pubkey = trimmed.substring("bunker://".length).substringBefore('?')
        val query = trimmed.substringAfter('?', "")
        return hex64.matches(pubkey) && query.split('&').any { it.startsWith("relay=") && it.length > "relay=".length }
    }

    /** Scanned QR content: some signers prefix the URI with `nostr:` or add whitespace. */
    fun cleanScanned(content: String?): String? {
        val trimmed = content?.trim()?.removePrefix("nostr:") ?: return null
        return trimmed.takeIf { isBunkerUri(it) }
    }

    /** nsec1… (bech32) or 64-hex private key. Decoding happens in AccountManager. */
    fun looksLikeSecretKey(input: String): Boolean {
        val trimmed = input.trim()
        return nsec.matches(trimmed.lowercase()) || hex64.matches(trimmed)
    }

    /** kind 0 fields for a new account, same keys as web (`name` + `display_name`, `about`). Blank fields are omitted. */
    fun profileChanges(name: String, about: String): Map<String, String?> = buildMap {
        val n = name.trim()
        val a = about.trim()
        if (n.isNotEmpty()) {
            put("name", n)
            put("display_name", n)
        }
        if (a.isNotEmpty()) put("about", a)
    }
}

/** NIP-55 permissions requested at login so Amber can sign in the background later. */
object AmberPermissions {
    val all: List<Permission> = Nip46Permissions.SIGN_KINDS.map { Permission(CommandType.SIGN_EVENT, it) } + listOf(
        Permission(CommandType.NIP04_ENCRYPT),
        Permission(CommandType.NIP04_DECRYPT),
        Permission(CommandType.NIP44_ENCRYPT),
        Permission(CommandType.NIP44_DECRYPT),
        Permission(CommandType.DECRYPT_ZAP_EVENT),
    )
}
