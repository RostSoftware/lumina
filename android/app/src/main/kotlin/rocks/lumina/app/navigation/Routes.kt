package rocks.lumina.app.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. */
sealed interface Route {
    @Serializable data object Home : Route
    @Serializable data class Discover(val hashtag: String? = null) : Route
    @Serializable data object Create : Route
    @Serializable data object Notifications : Route
    @Serializable data object MyProfile : Route
    @Serializable data class Profile(val pubkey: String) : Route
    @Serializable data class Post(val id: String, val relays: List<String> = emptyList(), val author: String? = null, val focusComment: Boolean = false) : Route
    @Serializable data class Media(val postId: String, val index: Int) : Route
    @Serializable data object Login : Route
    @Serializable data object Messages : Route
    @Serializable data class Chat(val pubkey: String) : Route
    @Serializable data object Settings : Route
    @Serializable data object SettingsRelays : Route
    @Serializable data object SettingsMedia : Route
    @Serializable data object SettingsWallet : Route
    @Serializable data object SettingsAppearance : Route
    @Serializable data object SettingsContent : Route
    @Serializable data object SettingsAccount : Route
    @Serializable data object EditProfile : Route
    @Serializable data class FollowList(val pubkey: String, val followers: Boolean) : Route
}
