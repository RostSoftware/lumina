package rocks.lumina.app.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rocks.lumina.app.core.nostr.di.ApplicationScope
import rocks.lumina.app.core.signer.AccountManager

public enum class AppTheme { SYSTEM, LIGHT, DARK }

/** Settings for the active account (theme is device-wide). */
public data class AppSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val showSensitive: Boolean = false,
    val defaultZapSats: Long = 21,
    val useDefaultBlossomServers: Boolean = true,
    val notificationsLastSeen: Long = 0,
    val systemNotifications: Boolean = true,
)

/** DataStore-backed preferences. Per-account keys are suffixed with the pubkey. */
@Singleton
public class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val accountManager: AccountManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val store = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("lumina_settings") }
    private val themeKey = stringPreferencesKey("theme")

    private fun sensitiveKey(pk: String) = booleanPreferencesKey("sensitive:$pk")
    private fun zapKey(pk: String) = longPreferencesKey("zap:$pk")
    private fun blossomDefaultsKey(pk: String) = booleanPreferencesKey("blossomDefaults:$pk")
    private fun notifSeenKey(pk: String) = longPreferencesKey("notifSeen:$pk")
    private fun sysNotifKey(pk: String) = booleanPreferencesKey("sysNotif:$pk")
    private fun dmSeenKey(pk: String) = stringPreferencesKey("dmSeen:$pk")
    private fun dmReadKey(pk: String) = stringPreferencesKey("dmRead:$pk")
    private fun notifiedKey(pk: String) = longPreferencesKey("notified:$pk")

    init {
        scope.launch {
            accountManager.loggedOut.collect { pk ->
                store.edit { prefs -> prefs.asMap().keys.filter { it.name.endsWith(":$pk") }.forEach { prefs.remove(it) } }
            }
        }
    }

    public val settings: StateFlow<AppSettings> = combine(store.data, accountManager.active) { prefs, active ->
        read(prefs, active?.pubkey)
    }.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    private fun read(prefs: Preferences, pk: String?): AppSettings = AppSettings(
        theme = prefs[themeKey]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() } ?: AppTheme.SYSTEM,
        showSensitive = pk?.let { prefs[sensitiveKey(it)] } ?: false,
        defaultZapSats = pk?.let { prefs[zapKey(it)] } ?: 21L,
        useDefaultBlossomServers = pk?.let { prefs[blossomDefaultsKey(it)] } ?: true,
        notificationsLastSeen = pk?.let { prefs[notifSeenKey(it)] } ?: 0L,
        systemNotifications = pk?.let { prefs[sysNotifKey(it)] } ?: true,
    )

    public suspend fun settingsFor(pubkey: String): AppSettings = read(store.data.first(), pubkey)

    public fun setTheme(theme: AppTheme) {
        scope.launch { store.edit { it[themeKey] = theme.name } }
    }

    private fun editActive(block: (androidx.datastore.preferences.core.MutablePreferences, String) -> Unit) {
        val pk = accountManager.activePubkey ?: return
        scope.launch { store.edit { block(it, pk) } }
    }

    public fun setShowSensitive(value: Boolean): Unit = editActive { p, pk -> p[sensitiveKey(pk)] = value }
    public fun setDefaultZap(sats: Long): Unit = editActive { p, pk -> p[zapKey(pk)] = sats }
    public fun setUseDefaultBlossom(value: Boolean): Unit = editActive { p, pk -> p[blossomDefaultsKey(pk)] = value }
    public fun setSystemNotifications(value: Boolean): Unit = editActive { p, pk -> p[sysNotifKey(pk)] = value }

    public suspend fun markNotificationsSeen(pubkey: String, timestamp: Long) {
        store.edit { it[notifSeenKey(pubkey)] = maxOf(timestamp, it[notifSeenKey(pubkey)] ?: 0) }
    }

    /** Newest notification timestamp already shown as a system notification (worker bookkeeping). */
    public suspend fun lastSystemNotified(pubkey: String): Long = store.data.first()[notifiedKey(pubkey)] ?: 0L

    public suspend fun setLastSystemNotified(pubkey: String, timestamp: Long) {
        store.edit { it[notifiedKey(pubkey)] = timestamp }
    }

    /** Gift-wrap ids already seen on the Messages screen (drives the unread-DM dot without decrypting). */
    public suspend fun seenWrapIds(pubkey: String): Set<String> =
        store.data.first()[dmSeenKey(pubkey)]?.split(',')?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    public suspend fun addSeenWrapIds(pubkey: String, ids: Collection<String>) {
        if (ids.isEmpty()) return
        store.edit { prefs ->
            val existing = prefs[dmSeenKey(pubkey)]?.split(',')?.filter { it.isNotEmpty() } ?: emptyList()
            prefs[dmSeenKey(pubkey)] = (existing + ids).distinct().takeLast(MAX_SEEN_IDS).joinToString(",")
        }
    }

    /** Per-conversation "read up to" timestamps. */
    public suspend fun dmReadMarkers(pubkey: String): Map<String, Long> =
        store.data.first()[dmReadKey(pubkey)]?.split(',')?.mapNotNull { entry ->
            val parts = entry.split(':')
            if (parts.size == 2) parts[1].toLongOrNull()?.let { parts[0] to it } else null
        }?.toMap() ?: emptyMap()

    public suspend fun setDmRead(pubkey: String, peer: String, timestamp: Long) {
        store.edit { prefs ->
            val map = (prefs[dmReadKey(pubkey)]?.split(',')?.mapNotNull { e ->
                val p = e.split(':'); if (p.size == 2) p[1].toLongOrNull()?.let { p[0] to it } else null
            }?.toMap() ?: emptyMap()).toMutableMap()
            map[peer] = maxOf(timestamp, map[peer] ?: 0)
            prefs[dmReadKey(pubkey)] = map.entries.joinToString(",") { "${it.key}:${it.value}" }
        }
    }

    public val themeFlow: kotlinx.coroutines.flow.Flow<AppTheme> = settings.map { it.theme }

    private companion object {
        const val MAX_SEEN_IDS = 2000
    }
}
