package rocks.lumina.app.core.signer

import kotlinx.serialization.Serializable

/** How an account signs. The private key only lives in LUMINA for [INTERNAL]. */
@Serializable
public enum class SignerType {
    /** nsec stored encrypted with the Android Keystore. */
    INTERNAL,

    /** Amber or another NIP-55 signer app. */
    AMBER,

    /** NIP-46 remote signer (bunker:// or nostrconnect://). */
    NIP46,
}

/** Non-secret account metadata, persisted in DataStore. Secrets live in [SecureStore]. */
@Serializable
public data class Account(
    val pubkey: String,
    val type: SignerType,
    /** NIP-55 signer package (e.g. `com.greenart7c3.nostrsigner`). */
    val packageName: String? = null,
    /** NIP-46: the remote signer's pubkey and relays. */
    val remotePubkey: String? = null,
    val bunkerRelays: List<String> = emptyList(),
    val addedAt: Long = 0,
)
