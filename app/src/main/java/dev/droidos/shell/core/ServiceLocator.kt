package dev.droidos.shell.core

import android.content.Context
import dev.droidos.shell.data.SettingsRepository
import dev.droidos.shell.firewall.FirewallController
import dev.droidos.shell.platform.PlatformBridge
import dev.droidos.shell.shell.apps.AppRepository
import dev.droidos.shell.shell.notifications.NotificationCenter
import dev.droidos.shell.security.CryptoManager
import dev.droidos.shell.security.IntegrityVerifier
import dev.droidos.shell.security.PermissionAudit
import dev.droidos.shell.security.PrivacyMonitor
import dev.droidos.shell.shell.controls.SystemControls
import dev.droidos.shell.shell.recents.RecentsRepository
import dev.droidos.shell.system.DeviceStatsRepository
import dev.droidos.shell.updates.UpdateRepository
import java.io.File

/**
 * Small explicit dependency container. Everything is created lazily against the
 * application context, so services (VPN, notification listener) and UI share one instance
 * without a DI framework.
 */
object ServiceLocator {

    private lateinit var applicationContext: Context
    private var initialised = false

    val context: Context get() = applicationContext

    fun init(context: Context) {
        if (initialised) return
        applicationContext = context.applicationContext
        AppLog.init(File(applicationContext.filesDir, "logs"))
        CrashGuard.install(applicationContext.filesDir)
        initialised = true
        AppLog.i("ServiceLocator", "Container initialised")
    }

    val settings: SettingsRepository by lazy { SettingsRepository(applicationContext) }
    val crypto: CryptoManager by lazy { CryptoManager(applicationContext) }
    val permissionAudit: PermissionAudit by lazy { PermissionAudit(applicationContext) }
    val integrity: IntegrityVerifier by lazy { IntegrityVerifier(applicationContext, settings) }
    val notificationCenter: NotificationCenter by lazy { NotificationCenter(applicationContext) }

    /**
     * Bound to `dev.droidos.platform` when a device image provides it. On a retail phone the bind
     * fails and every consumer keeps its in-process path.
     */
    val platform: PlatformBridge by lazy { PlatformBridge(applicationContext).also { it.start() } }
    val privacy: PrivacyMonitor by lazy { PrivacyMonitor(applicationContext, platform) }
    val firewall: FirewallController by lazy {
        FirewallController(applicationContext, settings, platform)
    }
    val apps: AppRepository by lazy { AppRepository(applicationContext) }
    val recents: RecentsRepository by lazy { RecentsRepository(applicationContext) }
    val deviceStats: DeviceStatsRepository by lazy { DeviceStatsRepository(applicationContext) }
    val controls: SystemControls by lazy { SystemControls(applicationContext) }
    val updates: UpdateRepository by lazy { UpdateRepository(applicationContext, settings) }
}
