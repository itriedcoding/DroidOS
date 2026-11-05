package dev.droidos.shell.security

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import dev.droidos.shell.core.AppLog

/** A permission that can observe or control the user, surfaced in the privacy audit. */
data class SensitivePermission(val name: String, val label: String, val group: String)

object SensitivePermissions {
    val ALL: List<SensitivePermission> = listOf(
        SensitivePermission(Manifest.permission.CAMERA, "Camera", "Sensors"),
        SensitivePermission(Manifest.permission.RECORD_AUDIO, "Microphone", "Sensors"),
        SensitivePermission(Manifest.permission.ACCESS_FINE_LOCATION, "Precise location", "Location"),
        SensitivePermission(Manifest.permission.ACCESS_COARSE_LOCATION, "Approximate location", "Location"),
        SensitivePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION, "Background location", "Location"),
        SensitivePermission(Manifest.permission.READ_CONTACTS, "Contacts", "Personal data"),
        SensitivePermission(Manifest.permission.READ_SMS, "Read messages", "Personal data"),
        SensitivePermission(Manifest.permission.SEND_SMS, "Send messages", "Personal data"),
        SensitivePermission(Manifest.permission.READ_CALL_LOG, "Call history", "Personal data"),
        SensitivePermission(Manifest.permission.READ_PHONE_STATE, "Phone state", "Device"),
        SensitivePermission(Manifest.permission.BODY_SENSORS, "Body sensors", "Sensors"),
        SensitivePermission(Manifest.permission.READ_EXTERNAL_STORAGE, "Read files", "Storage"),
        SensitivePermission(Manifest.permission.MANAGE_EXTERNAL_STORAGE, "All files access", "Storage"),
        SensitivePermission(Manifest.permission.SYSTEM_ALERT_WINDOW, "Draw over apps", "Control"),
        SensitivePermission(Manifest.permission.REQUEST_INSTALL_PACKAGES, "Install apps", "Control"),
        SensitivePermission(Manifest.permission.QUERY_ALL_PACKAGES, "List all apps", "Device")
    )
}

data class AppPermissionStatus(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val granted: List<SensitivePermission>
)

/**
 * Reads the real per-package permission grants from PackageManager so the privacy screen
 * reports what the OS actually allows, not what the app claims.
 */
class PermissionAudit(private val context: Context) {

    fun appsWithSensitivePermissions(includeSystem: Boolean = false): List<AppPermissionStatus> {
        val packageManager = context.packageManager
        return try {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS).mapNotNull { info ->
                val isSystem = (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                if (!includeSystem && isSystem) return@mapNotNull null
                if (info.packageName == context.packageName) return@mapNotNull null

                val granted = SensitivePermissions.ALL.filter { permission ->
                    packageManager.checkPermission(permission.name, info.packageName) ==
                        PackageManager.PERMISSION_GRANTED
                }
                if (granted.isEmpty()) return@mapNotNull null

                AppPermissionStatus(
                    packageName = info.packageName,
                    label = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: info.packageName,
                    isSystem = isSystem,
                    granted = granted
                )
            }.sortedWith(compareByDescending<AppPermissionStatus> { it.granted.size }.thenBy { it.label })
        } catch (e: Exception) {
            AppLog.e("PermissionAudit", "Could not enumerate package permissions", e)
            emptyList()
        }
    }

    fun permissionHolders(permission: String): List<String> {
        val packageManager = context.packageManager
        return runCatching {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
                .filter { packageManager.checkPermission(permission, it.packageName) == PackageManager.PERMISSION_GRANTED }
                .map { it.packageName }
                .filter { it != context.packageName }
        }.getOrElse {
            AppLog.w("PermissionAudit", "Could not resolve holders for $permission", it)
            emptyList()
        }
    }
}
