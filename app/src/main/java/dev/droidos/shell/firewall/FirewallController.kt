package dev.droidos.shell.firewall

import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import dev.droidos.shell.data.SettingsRepository

/**
 * Owns the VPN consent handshake and the service lifecycle. The UI must call [prepareIntent]
 * and launch it for result before [start] can succeed; the OS requires that explicit consent.
 */
class FirewallController(
    private val context: Context,
    private val settingsRepository: SettingsRepository
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
        if (FirewallStatus.running.value) start(packages)
    }

    suspend fun enable(blocked: Set<String>): Outcome<Unit> = attempt("Enable firewall") {
        if (!hasConsent()) {
            throw IllegalStateException("VPN consent has not been granted yet")
        }
        settingsRepository.setFirewallBlocked(blocked)
        settingsRepository.setFirewallEnabled(true)
        start(blocked)
    }

    suspend fun disable(): Outcome<Unit> = attempt("Disable firewall") {
        settingsRepository.setFirewallEnabled(false)
        context.startService(DroidFirewallService.stopIntent(context))
        FirewallStatus.markStopped()
        Unit
    }

    private fun start(blocked: Set<String>) {
        val intent = Intent(context, DroidFirewallService::class.java)
            .setAction(DroidFirewallService.ACTION_START)
            .putExtra(EXTRA_BLOCKED, blocked.toTypedArray())
        context.startService(intent)
        AppLog.i("Firewall", "Requested firewall start with ${blocked.size} blocked apps")
    }

    /** True when the OS currently has a VPN transport, ours or another app's. */
    fun isVpnTransportActive(): Boolean = VpnTransport.isActive(context)

    companion object {
        const val EXTRA_BLOCKED = "blocked_packages"
    }
}
