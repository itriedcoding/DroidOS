package dev.droidos.shell.firewall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import dev.droidos.shell.MainActivity
import dev.droidos.shell.R
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The firewall. A local VpnService claims the default route and then excludes every app
 * that is allowed to use the network. Blocked apps are captured by the tunnel, which is
 * never read from, so their packets are dropped by the kernel. No root, no external server,
 * nothing leaves the device.
 */
class DroidFirewallService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var tunnel: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AppLog.i("Firewall", "Stop requested")
                stopTunnel()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startTunnel()
        }
        return START_STICKY
    }

    private fun startTunnel() {
        closeTunnelOnly()
        scope.launch {
            try {
                val settings = ServiceLocator.settings.current()
                val blocked = settings.firewallBlocked
                val installed = installedPackageNames()
                val allowed = FirewallRules.allowedPackages(installed, blocked, packageName)

                val builder = Builder()
                    .setSession(getString(R.string.firewall_session_name))
                    .setMtu(1500)
                    .addAddress("10.111.222.1", 32)
                    .addRoute("0.0.0.0", 0)
                    // Keep our own traffic out of the tunnel so update checks still work.
                    .addDisallowedApplication(packageName)

                var excluded = 0
                var missing = 0
                for (allowedPackage in allowed) {
                    try {
                        builder.addDisallowedApplication(allowedPackage)
                        excluded++
                    } catch (e: PackageManager.NameNotFoundException) {
                        missing++
                    }
                }

                val descriptor = builder.establish()
                if (descriptor == null) {
                    FirewallStatus.markError("The system refused to establish the tunnel. Another VPN may be active.")
                    AppLog.e("Firewall", "establish() returned null")
                    return@launch
                }
                tunnel = descriptor
                FirewallStatus.markRunning(blocked)
                AppLog.i(
                    "Firewall",
                    "Tunnel up: ${blocked.size} blocked, $excluded excluded, $missing unknown packages"
                )
                showNotification(blocked.size)
            } catch (e: Exception) {
                AppLog.e("Firewall", "Could not start firewall tunnel", e)
                FirewallStatus.markError(e.message ?: "Firewall failed to start")
                stopTunnel()
            }
        }
    }

    private fun closeTunnelOnly() {
        runCatching { tunnel?.close() }
        tunnel = null
    }

    private fun stopTunnel() {
        closeTunnelOnly()
        FirewallStatus.markStopped()
        cancelNotification()
    }

    override fun onRevoke() {
        AppLog.w("Firewall", "Tunnel revoked by the system or the user")
        closeTunnelOnly()
        FirewallStatus.markStopped()
        cancelNotification()
        scope.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        closeTunnelOnly()
        FirewallStatus.markStopped()
        scope.cancel()
        super.onDestroy()
    }

    private fun showNotification(blockedCount: Int) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Firewall", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Shown while the DroidOS firewall is active" }
            )
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).setData(android.net.Uri.parse("droidos://firewall")),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shortcut_firewall)
            .setContentTitle("Firewall active")
            .setContentText("$blockedCount apps denied network access")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            AppLog.w("Firewall", "Notification permission missing for firewall status", e)
        }
    }

    private fun cancelNotification() {
        runCatching { getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
    }

    companion object {
        const val ACTION_START = "dev.droidos.shell.firewall.START"
        const val ACTION_STOP = "dev.droidos.shell.firewall.STOP"
        private const val CHANNEL_ID = "droidos_firewall"
        private const val NOTIFICATION_ID = 4711

        fun stopIntent(context: Context): Intent =
            Intent(context, DroidFirewallService::class.java).setAction(ACTION_STOP)
    }
}
