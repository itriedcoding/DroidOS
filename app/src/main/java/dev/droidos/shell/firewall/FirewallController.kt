package dev.droidos.shell.firewall

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import dev.droidos.shell.data.SettingsRepository
import dev.droidos.shell.platform.PlatformBridge

/**
 * Owns the VPN consent handshake and the service lifecycle. The UI must call [prepareIntent]
 * and launch it for result before [start] can succeed; the OS requires that explicit consent.
 */
class FirewallController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val platform: PlatformBridge? = null
) {

    /** Non-null when the user still has to grant VPN consent. */
    fun prepareIntent(): Intent? = try {
        VpnService.prepare(context)
    } catch (e: Exception) {
        AppLog.e("Firewall", "VpnService.prepare failed", e)
        null
    }

    fun hasConsent(): Boolean = prepareIntent() == null

    suspend fun setBlocked(packages: Set<String>): Outcome<Unit> = attempt("Persist firewall rules") {
        settingsRepository.setFirewallBlocked(packages)
        if (FirewallStatus.running.value) {
            start(packages)
            applyKernelRules(packages)
        }
    }

    suspend fun enable(blocked: Set<String>): Outcome<Unit> = attempt("Enable firewall") {
        if (!hasConsent()) {
            throw IllegalStateException("VPN consent has not been granted yet")
        }
        settingsRepository.setFirewallBlocked(blocked)
        settingsRepository.setFirewallEnabled(true)
        start(blocked)
        applyKernelRules(blocked)
    }

    suspend fun disable(): Outcome<Unit> = attempt("Disable firewall") {
        settingsRepository.setFirewallEnabled(false)
        context.startService(DroidFirewallService.stopIntent(context))
        FirewallStatus.markStopped()
        if (platform?.isAvailable == true) {
            AppLog.i("Firewall", "Kernel egress rules cleared=${platform.clearBlocklist()}")
        }
        Unit
    }

    private fun start(blocked: Set<String>) {
        val intent = Intent(context, DroidFirewallService::class.java)
            .setAction(DroidFirewallService.ACTION_START)
            .putExtra(EXTRA_BLOCKED, blocked.toTypedArray())
        context.startService(intent)
        AppLog.i("Firewall", "Requested firewall start with ${blocked.size} blocked apps")
    }

    /**
     * Installs the kernel egress rules through the platform service.
     *
     * Returns the number of rules applied, or null when no service is bound - in which case the
     * VPN tunnel remains the only blocker and nothing is claimed to have happened.
     */
    private fun applyKernelRules(blocked: Set<String>): Int? {
        val bridge = platform ?: return null
        if (!bridge.isAvailable) return null
        val uids = resolveUids(blocked)
        if (uids.isEmpty()) return 0
        val applied = bridge.applyBlocklist(uids)
        AppLog.i("Firewall", "Kernel rules requested=${uids.size} applied=${applied ?: -1}")
        return applied
    }

    /**
     * Package name to UID, read from the package manager. A package uninstalled since it was
     * blocked is skipped and logged rather than guessed at.
     */
    private fun resolveUids(packages: Set<String>): IntArray {
        val manager = context.packageManager
        return packages.mapNotNull { name ->
            runCatching { manager.getApplicationInfo(name, 0).uid }
                .onFailure { AppLog.w("Firewall", "No UID for $name; it is not installed") }
                .getOrNull()
        }.distinct().toIntArray()
    }

    /** True when the OS currently has a VPN transport, ours or another app's. */
    fun isVpnTransportActive(): Boolean = VpnTransport.isActive(context)

    companion object {
        const val EXTRA_BLOCKED = "blocked_packages"
    }
}
