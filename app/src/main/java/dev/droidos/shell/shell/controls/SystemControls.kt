package dev.droidos.shell.shell.controls

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Result of an attempt to change a system setting. */
data class ControlResult(
    val applied: Boolean,
    val message: String? = null,
    val needsUserAction: Intent? = null
)

/**
 * Genuine system controls. Several Android settings can only be changed by a system app, so
 * each control reports exactly what happened instead of pretending: either the value really
 * changed, or the platform refused and the user is handed the official panel.
 */
class SystemControls(private val context: Context) {

    private val wifiManager: WifiManager? = context.applicationContext
        .getSystemService(WifiManager::class.java)
    private val notificationManager: NotificationManager? =
        context.getSystemService(NotificationManager::class.java)
    private val cameraManager: CameraManager? = context.getSystemService(CameraManager::class.java)

    // ------------------------------------------------------------------ Wi-Fi
    fun isWifiEnabled(): Boolean = wifiManager?.isWifiEnabled ?: false

    fun isWifiSupported(): Boolean = wifiManager != null

    fun setWifi(enabled: Boolean): ControlResult {
        val manager = wifiManager ?: return ControlResult(false, "This device has no Wi-Fi adapter")
        return try {
            @Suppress("DEPRECATION")
            val applied = manager.setWifiEnabled(enabled)
            if (applied) {
                ControlResult(true)
            } else {
                ControlResult(
                    applied = false,
                    message = "Android ${Build.VERSION.RELEASE} only lets system apps switch Wi-Fi directly",
                    needsUserAction = wifiPanelIntent()
                )
            }
        } catch (e: SecurityException) {
            AppLog.w("SystemControls", "Wi-Fi toggle denied", e)
            ControlResult(false, "Wi-Fi control is restricted on this build", wifiPanelIntent())
        }
    }

    fun wifiPanelIntent(): Intent = Intent(Settings.Panel.ACTION_WIFI)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    // -------------------------------------------------------------- Bluetooth
    fun isBluetoothEnabled(): Boolean = try {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
        adapter?.isEnabled ?: false
    } catch (e: SecurityException) {
        AppLog.w("SystemControls", "Bluetooth state needs BLUETOOTH_CONNECT", e)
        false
    }

    fun isBluetoothSupported(): Boolean = try {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter != null
    } catch (e: Exception) {
        false
    }

    /** Enabling asks the user in a system dialog, which is the supported path for apps. */
    fun bluetoothEnableIntent(): Intent =
        Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun setBluetooth(enabled: Boolean): ControlResult {
        if (enabled) {
            return ControlResult(false, "Confirm Bluetooth in the system dialog", bluetoothEnableIntent())
        }
        return try {
            @Suppress("DEPRECATION")
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE)
                as? android.bluetooth.BluetoothManager)?.adapter
                ?: return ControlResult(false, "This device has no Bluetooth adapter")
            @Suppress("DEPRECATION")
            val applied = adapter.disable()
            if (applied) {
                ControlResult(true)
            } else {
                ControlResult(
                    false,
                    "Android requires the system panel to turn Bluetooth off",
                    Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: SecurityException) {
            AppLog.w("SystemControls", "Bluetooth disable denied", e)
            ControlResult(
                false,
                "Bluetooth control is restricted on this build",
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    // ------------------------------------------------------------ Do not disturb
    fun hasDndAccess(): Boolean = notificationManager?.isNotificationPolicyAccessGranted ?: false

    fun isDndEnabled(): Boolean = try {
        notificationManager?.currentInterruptionFilter?.let {
            it != NotificationManager.INTERRUPTION_FILTER_ALL
        } ?: false
    } catch (e: Exception) {
        false
    }

    fun setDnd(enabled: Boolean): ControlResult {
        val manager = notificationManager ?: return ControlResult(false, "Notification service unavailable")
        if (!manager.isNotificationPolicyAccessGranted) {
            return ControlResult(
                false,
                "Grant Do Not Disturb access to control it from here",
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return try {
            manager.setInterruptionFilter(
                if (enabled) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
            ControlResult(true)
        } catch (e: Exception) {
            AppLog.e("SystemControls", "Could not change interruption filter", e)
            ControlResult(false, e.message ?: "Do Not Disturb could not be changed")
        }
    }
}
