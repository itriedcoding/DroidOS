package dev.droidos.shell.shell.recents

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.ServiceLocator

data class RecentEntry(
    val packageName: String,
    val label: String,
    val lastUsed: Long,
    val totalTimeInForegroundMs: Long
)

/**
 * Real multitasking data. Android no longer exposes other apps' live task stacks to
 * third-party launchers, so recents are derived from the usage-stats event stream the OS
 * keeps for exactly this purpose. Requires the PACKAGE_USAGE_STATS special access.
 */
class RecentsRepository(private val context: Context) {

    fun hasUsageAccess(): Boolean = try {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        appOps?.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) {
        AppLog.w("Recents", "Could not read usage access state", e)
        false
    }

    fun usageAccessIntent(): android.content.Intent =
        android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

    fun recent(limit: Int = 12, windowMillis: Long = 24L * 60L * 60L * 1000L): List<RecentEntry> {
        if (!hasUsageAccess()) return emptyList()
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        return try {
            val stats = manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - windowMillis,
                now
            ) ?: return emptyList()

            stats.asSequence()
                .filter { it.lastTimeUsed > 0 }
                .groupBy { it.packageName }
                .mapNotNull { (packageName, entries) ->
                    if (packageName == context.packageName) return@mapNotNull null
                    if (!ServiceLocator.apps.isInstalled(packageName)) return@mapNotNull null
                    RecentEntry(
                        packageName = packageName,
                        label = ServiceLocator.apps.labelFor(packageName),
                        lastUsed = entries.maxOf { it.lastTimeUsed },
                        totalTimeInForegroundMs = entries.sumOf { it.totalTimeInForeground }
                    )
                }
                .sortedByDescending { it.lastUsed }
                .take(limit)
                .toList()
        } catch (e: Exception) {
            AppLog.e("Recents", "Usage stats query failed", e)
            emptyList()
        }
    }
}
