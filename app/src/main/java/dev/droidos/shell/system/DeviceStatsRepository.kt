package dev.droidos.shell.system

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Fmt
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Real device telemetry: battery, memory, storage and active network transport. */
class DeviceStatsRepository(private val context: Context) {

    data class Battery(
        val percent: Int = 0,
        val charging: Boolean = false,
        val plugged: String = "unplugged",
        val temperatureC: Float = 0f,
        val voltageMv: Int = 0,
        val health: String = "unknown",
        val technology: String = "unknown",
        val currentNowMicroAmps: Long = 0L,
        val chargeCounterMicroAh: Long = 0L
    ) {
        val summary: String
            get() = buildString {
                append("$percent%")
                if (charging) append(" - charging")
            }
    }

    data class Storage(val totalBytes: Long, val freeBytes: Long) {
        val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
        val usedFraction: Float
            get() = if (totalBytes <= 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
        val summary: String get() = "${Fmt.bytes(usedBytes)} of ${Fmt.bytes(totalBytes)} used"
    }

    data class Memory(val totalBytes: Long, val availableBytes: Long, val lowMemory: Boolean) {
        val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
        val usedFraction: Float
            get() = if (totalBytes <= 0L) 0f else usedBytes.toFloat() / totalBytes.toFloat()
    }

    data class Network(
        val connected: Boolean,
        val transport: String,
        val validated: Boolean,
        val metered: Boolean
    )

    fun batteryFlow(): Flow<Battery> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent?.let { trySend(it.toBattery()) }
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = context.registerReceiver(receiver, filter)
        sticky?.let { trySend(it.toBattery()) }
        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }.distinctUntilChanged()

    private fun Intent.toBattery(): Battery {
        val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        val percent = if (level < 0) 0 else (level * 100 / scale)
        val status = getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = when (getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "unplugged"
        }
        val health = when (getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "over voltage"
            BatteryManager.BATTERY_HEALTH_COLD -> "cold"
            else -> "unknown"
        }
        val manager = context.getSystemService(BatteryManager::class.java)
        return Battery(
            percent = percent,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
            plugged = plugged,
            temperatureC = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            voltageMv = getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            health = health,
            technology = getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "unknown",
            currentNowMicroAmps = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0L,
            chargeCounterMicroAh = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: 0L
        )
    }

    fun storage(): Storage = try {
        val stat = StatFs(Environment.getDataDirectory().absolutePath)
        Storage(totalBytes = stat.blockCountLong * stat.blockSizeLong, freeBytes = stat.availableBlocksLong * stat.blockSizeLong)
    } catch (e: Exception) {
        AppLog.e("DeviceStats", "Storage query failed", e)
        Storage(0L, 0L)
    }

    fun memory(): Memory {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        return try {
            manager?.getMemoryInfo(info)
            Memory(
                totalBytes = info.totalMem,
                availableBytes = info.availMem,
                lowMemory = info.lowMemory
            )
        } catch (e: Exception) {
            AppLog.e("DeviceStats", "Memory query failed", e)
            Memory(0L, 0L, false)
        }
    }

    fun network(): Network {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        return try {
            val active = manager?.activeNetwork
            val capabilities = active?.let { manager.getNetworkCapabilities(it) }
            if (capabilities == null) {
                Network(false, "offline", false, false)
            } else {
                val transport = when {
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
                    else -> "connected"
                }
                Network(
                    connected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                    transport = transport,
                    validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    metered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                )
            }
        } catch (e: Exception) {
            AppLog.e("DeviceStats", "Network query failed", e)
            Network(false, "unknown", false, false)
        }
    }
}
