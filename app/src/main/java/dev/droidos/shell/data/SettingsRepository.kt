package dev.droidos.shell.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.data.model.AccentColor
import dev.droidos.shell.data.model.AppSettings
import dev.droidos.shell.data.model.ThemeMode
import dev.droidos.shell.data.model.WallpaperStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "droidos_settings")

/**
 * Persists every user preference with DataStore (atomic, corruption-resistant).
 * Reads are exposed as a hot Flow; writes are suspending and serialised by DataStore.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val accent = stringPreferencesKey("accent")
        val themeMode = stringPreferencesKey("theme_mode")
        val amoled = booleanPreferencesKey("amoled_black")
        val wallpaper = stringPreferencesKey("wallpaper")
        val gridColumns = intPreferencesKey("grid_columns")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinHash = stringPreferencesKey("pin_hash")
        val biometric = booleanPreferencesKey("biometric_enabled")
        val failedUnlocks = intPreferencesKey("failed_unlocks")
        val lockoutUntil = longPreferencesKey("lockout_until")
        val lockedApps = stringSetPreferencesKey("locked_apps")
        val firewallEnabled = booleanPreferencesKey("firewall_enabled")
        val firewallBlocked = stringSetPreferencesKey("firewall_blocked")
        val pinnedSigner = stringPreferencesKey("pinned_signer")
        val firstRunComplete = booleanPreferencesKey("first_run_complete")
        val notificationAsked = booleanPreferencesKey("notification_asked")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val updateChannel = stringPreferencesKey("update_channel_url")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            AppLog.e("SettingsRepo", "DataStore read failed, serving defaults", error)
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs -> prefs.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        accent = AccentColor.fromId(this[Keys.accent]),
        themeMode = ThemeMode.fromId(this[Keys.themeMode]),
        amoledBlack = this[Keys.amoled] ?: true,
        wallpaper = WallpaperStyle.fromId(this[Keys.wallpaper]),
        gridColumns = this[Keys.gridColumns] ?: 0,
        lockEnabled = this[Keys.lockEnabled] ?: false,
        pinSalt = this[Keys.pinSalt],
        pinHash = this[Keys.pinHash],
        biometricEnabled = this[Keys.biometric] ?: false,
        failedUnlocks = this[Keys.failedUnlocks] ?: 0,
        lockoutUntil = this[Keys.lockoutUntil] ?: 0L,
        lockedApps = this[Keys.lockedApps] ?: emptySet(),
        firewallEnabled = this[Keys.firewallEnabled] ?: false,
        firewallBlocked = this[Keys.firewallBlocked] ?: emptySet(),
        pinnedSignerDigest = this[Keys.pinnedSigner],
        firstRunComplete = this[Keys.firstRunComplete] ?: false,
        onboardingNotificationAsked = this[Keys.notificationAsked] ?: false,
        lastUpdateCheck = this[Keys.lastUpdateCheck] ?: 0L,
        updateChannelUrl = this[Keys.updateChannel] ?: "",
        showSecondsInClock = this[Keys.showSeconds] ?: false,
        autoUpdateCheck = this[Keys.autoUpdate] ?: true
    )

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.settingsDataStore.edit(block)
        } catch (e: Exception) {
            AppLog.e("SettingsRepo", "DataStore write failed", e)
        }
    }

    suspend fun setAccent(accent: AccentColor) = update { it[Keys.accent] = accent.name }
    suspend fun setThemeMode(mode: ThemeMode) = update { it[Keys.themeMode] = mode.name }
    suspend fun setAmoled(enabled: Boolean) = update { it[Keys.amoled] = enabled }
    suspend fun setWallpaper(style: WallpaperStyle) = update { it[Keys.wallpaper] = style.name }
    suspend fun setGridColumns(columns: Int) = update { it[Keys.gridColumns] = columns.coerceIn(0, 7) }
    suspend fun setShowSeconds(enabled: Boolean) = update { it[Keys.showSeconds] = enabled }
    suspend fun setAutoUpdateCheck(enabled: Boolean) = update { it[Keys.autoUpdate] = enabled }

    suspend fun setLock(pinSalt: String?, pinHash: String?) = update {
        if (pinSalt == null || pinHash == null) {
            it.remove(Keys.pinSalt)
            it.remove(Keys.pinHash)
            it[Keys.lockEnabled] = false
            it[Keys.failedUnlocks] = 0
            it[Keys.lockoutUntil] = 0L
        } else {
            it[Keys.pinSalt] = pinSalt
            it[Keys.pinHash] = pinHash
            it[Keys.lockEnabled] = true
            it[Keys.failedUnlocks] = 0
            it[Keys.lockoutUntil] = 0L
        }
    }

    suspend fun setBiometric(enabled: Boolean) = update { it[Keys.biometric] = enabled }

    suspend fun setLockout(failed: Int, until: Long) = update {
        it[Keys.failedUnlocks] = failed
        it[Keys.lockoutUntil] = until
    }

    suspend fun setLockedApps(packages: Set<String>) = update { it[Keys.lockedApps] = packages }
    suspend fun setFirewallEnabled(enabled: Boolean) = update { it[Keys.firewallEnabled] = enabled }
    suspend fun setFirewallBlocked(packages: Set<String>) = update { it[Keys.firewallBlocked] = packages }
    suspend fun setPinnedSigner(digest: String) = update { it[Keys.pinnedSigner] = digest }
    suspend fun setFirstRunComplete() = update { it[Keys.firstRunComplete] = true }
    suspend fun setNotificationAsked() = update { it[Keys.notificationAsked] = true }
    suspend fun setLastUpdateCheck(timestamp: Long) = update { it[Keys.lastUpdateCheck] = timestamp }
    suspend fun setUpdateChannelUrl(url: String) = update { it[Keys.updateChannel] = url }
}
