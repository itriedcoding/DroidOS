package dev.droidos.shell.firewall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Restores the firewall after a reboot or an app update when the user had it enabled.
 * Starting a service from BOOT_COMPLETED can be refused by the platform; that refusal is
 * logged and surfaced in the firewall screen rather than hidden.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = ServiceLocator.settings.current()
                if (!settings.firewallEnabled) return@launch

                val start = Intent(context, DroidFirewallService::class.java)
                    .setAction(DroidFirewallService.ACTION_START)
                try {
                    context.startService(start)
                    AppLog.i("BootReceiver", "Firewall restart requested after $action")
                } catch (e: Exception) {
                    AppLog.e("BootReceiver", "Platform refused firewall restart after $action", e)
                    FirewallStatus.markError("Firewall could not restart automatically. Open the firewall and enable it again.")
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
