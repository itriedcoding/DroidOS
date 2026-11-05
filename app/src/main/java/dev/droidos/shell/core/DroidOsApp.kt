package dev.droidos.shell.core

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.droidos.shell.MainActivity
import dev.droidos.shell.updates.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DroidOsApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        scope.launch { verifyIntegrity() }
        scope.launch { autoCheckForUpdates() }
    }

    /** Tamper detection runs once per launch; a changed signing identity is logged loudly. */
    private suspend fun verifyIntegrity() {
        when (val result = ServiceLocator.integrity.verify()) {
            is dev.droidos.shell.security.IntegrityVerifier.Result.Trusted ->
                AppLog.i("DroidOsApp", "Integrity trusted (first pin: ${result.firstPin})")
            is dev.droidos.shell.security.IntegrityVerifier.Result.Changed ->
                AppLog.e("DroidOsApp", "Signing identity mismatch: ${result.expected} != ${result.actual}")
            is dev.droidos.shell.security.IntegrityVerifier.Result.Unavailable ->
                AppLog.w("DroidOsApp", "Integrity unavailable: ${result.reason}")
        }
    }

    /**
     * Signed-channel check, at most once per day, only while the user keeps automatic checks on.
     * It never installs anything: finding a build only raises a notification, and the install
     * itself stays a user-confirmed system dialog.
     */
    private suspend fun autoCheckForUpdates() {
        val settings = try {
            ServiceLocator.settings.current()
        } catch (e: Exception) {
            AppLog.w("DroidOsApp", "Could not read update preference", e)
            return
        }
        if (!settings.autoUpdateCheck) {
            AppLog.i("DroidOsApp", "Automatic update checks are disabled")
            return
        }
        val age = System.currentTimeMillis() - settings.lastUpdateCheck
        if (settings.lastUpdateCheck > 0L && age in 0..AUTO_CHECK_INTERVAL_MILLIS) return

        when (val result = ServiceLocator.updates.check()) {
            is UpdateRepository.CheckResult.Available -> {
                AppLog.i("DroidOsApp", "Update ${result.manifest.versionName} available")
                notifyUpdateAvailable(result.manifest.versionName)
            }
            is UpdateRepository.CheckResult.Failed ->
                AppLog.w("DroidOsApp", "Automatic update check failed: ${result.reason}")
            is UpdateRepository.CheckResult.UpToDate ->
                AppLog.i("DroidOsApp", "Automatic update check: already current")
        }
    }

    private fun notifyUpdateAvailable(versionName: String) {
        val manager = NotificationManagerCompat.from(this)
        if (!manager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_UPDATES,
                "System updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "New DroidOS builds on the signed update channel" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("DroidOS $versionName is available")
            .setContentText("Open Settings, System update to review and install it")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(NOTIFICATION_UPDATE, notification)
        } catch (e: SecurityException) {
            AppLog.w("DroidOsApp", "Update notification blocked", e)
        }
    }

    private companion object {
        const val CHANNEL_UPDATES = "droidos_updates"
        const val NOTIFICATION_UPDATE = 4200
        const val AUTO_CHECK_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L
    }
}
