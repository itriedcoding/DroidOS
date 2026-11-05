package dev.droidos.shell.firewall

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dev.droidos.shell.core.AppLog

/** Pure rule maths so the block list behaviour is unit-tested independently of the VPN. */
object FirewallRules {

    /**
     * The VPN tunnels every app we are allowed to reach the network with and lets them
     * through; everything else is captured by the tunnel and dropped. So the set handed to
     * VpnService.Builder.addDisallowedApplication is "installed - blocked - self".
     */
    fun allowedPackages(installed: Collection<String>, blocked: Collection<String>, self: String): List<String> =
        installed.asSequence()
            .filter { it != self }
            .filterNot { it in blocked }
            .distinct()
            .sorted()
            .toList()

    fun summary(installedCount: Int, blockedCount: Int): String =
        "$blockedCount of $installedCount installed apps denied network access"
}

object FirewallStatus {
    private val _running = kotlinx.coroutines.flow.MutableStateFlow(false)
    val running: kotlinx.coroutines.flow.StateFlow<Boolean> = _running

    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: kotlinx.coroutines.flow.StateFlow<String?> = _error

    private val _activeBlocked = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val activeBlocked: kotlinx.coroutines.flow.StateFlow<Set<String>> = _activeBlocked

    fun markRunning(blocked: Set<String>) {
        _activeBlocked.value = blocked
        _error.value = null
        _running.value = true
    }

    fun markStopped() {
        _running.value = false
        _activeBlocked.value = emptySet()
    }

    fun markError(message: String) {
        _error.value = message
        _running.value = false
    }

    fun clearError() {
        _error.value = null
    }
}

/** Detects whether *any* VPN transport is live, so the UI never lies about tunnel state. */
object VpnTransport {
    fun isActive(context: Context): Boolean = try {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
        manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
    } catch (e: Exception) {
        AppLog.w("VpnTransport", "Could not query network transports", e)
        false
    }
}

fun Context.installedPackageNames(): List<String> = try {
    packageManager.getInstalledPackages(PackageManager.GET_META_DATA).map { it.packageName }
} catch (e: Exception) {
    AppLog.e("Firewall", "Could not enumerate installed packages", e)
    emptyList()
}
