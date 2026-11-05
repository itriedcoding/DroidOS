package dev.droidos.shell.shell.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val versionName: String?
)

/**
 * Enumerates and launches real installed applications through PackageManager. Icons are
 * cached as ImageBitmap at a bounded size so scrolling the drawer does not re-decode
 * drawables or blow up memory.
 */
class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val iconCache = LruCache<String, ImageBitmap>(120)
    private val labelCache = mutableMapOf<String, String>()

    @Volatile
    private var cached: List<AppEntry>? = null

    suspend fun apps(forceRefresh: Boolean = false): List<AppEntry> = withContext(Dispatchers.IO) {
        cached?.takeIf { !forceRefresh }?.let { return@withContext it }

        val entries = try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            packageManager.queryIntentActivities(intent, 0)
                .mapNotNull { resolveInfo ->
                    val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val packageName = activityInfo.packageName
                    if (packageName == context.packageName) return@mapNotNull null
                    val appInfo = try {
                        packageManager.getApplicationInfo(packageName, 0)
                    } catch (e: PackageManager.NameNotFoundException) {
                        return@mapNotNull null
                    }
                    val label = resolveInfo.loadLabel(packageManager)?.toString()
                        ?: appInfo.loadLabel(packageManager)?.toString()
                        ?: packageName
                    labelCache[packageName] = label
                    AppEntry(
                        packageName = packageName,
                        label = label,
                        isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionName = runCatching {
                            packageManager.getPackageInfo(packageName, 0).versionName
                        }.getOrNull()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        } catch (e: Exception) {
            AppLog.e("AppRepository", "Could not enumerate launcher activities", e)
            emptyList()
        }

        cached = entries
        AppLog.i("AppRepository", "Loaded ${entries.size} launchable apps")
        entries
    }

    fun labelFor(packageName: String): String = labelCache[packageName] ?: run {
        val label = runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrDefault(packageName)
        labelCache[packageName] = label
        label
    }

    fun icon(packageName: String): ImageBitmap? {
        iconCache.get(packageName)?.let { return it }
        val drawable: Drawable = try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            AppLog.w("AppRepository", "Missing icon for $packageName", e)
            return null
        }
        return try {
            val bitmap: Bitmap = drawable.toBitmap(width = 144, height = 144)
            val image = bitmap.asImageBitmap()
            iconCache.put(packageName, image)
            image
        } catch (e: Exception) {
            AppLog.w("AppRepository", "Could not rasterise icon for $packageName", e)
            null
        }
    }

    fun launch(packageName: String): Outcome<Unit> {
        val intent = try {
            packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: Exception) {
            null
        } ?: return Outcome.Err("$packageName has no launchable activity")

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return attempt("Launching $packageName") {
            context.startActivity(intent)
            AppLog.i("AppRepository", "Launched $packageName")
        }
    }

    fun openAppInfo(packageName: String): Outcome<Unit> = attempt("Open app info") {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun requestUninstall(packageName: String): Outcome<Unit> = attempt("Request uninstall") {
        context.startActivity(
            Intent(Intent.ACTION_DELETE)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun isInstalled(packageName: String): Boolean = runCatching {
        packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

    fun factsFor(packageName: String): dev.droidos.shell.security.PackageFacts? = try {
        val info = packageManager.getPackageInfo(
            packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES
        )
        val appInfo = info.applicationInfo ?: return null
        val installer = runCatching {
            packageManager.getInstallSourceInfo(packageName).installingPackageName
        }.getOrNull()
        val services = info.services ?: emptyArray()
        val hasAccessibility = services.any { service ->
            service.permission == "android.permission.BIND_ACCESSIBILITY_SERVICE"
        }
        val deviceAdmins = runCatching {
            (context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager)
                .isDeviceOwnerApp(packageName)
        }.getOrDefault(false)
        dev.droidos.shell.security.PackageFacts(
            packageName = packageName,
            label = appInfo.loadLabel(packageManager).toString(),
            installerPackage = installer,
            isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            targetSdk = appInfo.targetSdkVersion,
            requestedPermissions = (info.requestedPermissions ?: emptyArray()).toSet(),
            hasAccessibilityService = hasAccessibility,
            isDeviceAdmin = deviceAdmins,
            isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            firstInstallTime = info.firstInstallTime
        )
    } catch (e: Exception) {
        AppLog.w("AppRepository", "Could not read facts for $packageName", e)
        null
    }

    fun allFacts(): List<dev.droidos.shell.security.PackageFacts> =
        runCatching {
            packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
                .mapNotNull { factsFor(it.packageName) }
        }.getOrElse {
            AppLog.e("AppRepository", "Could not gather package facts", it)
            emptyList()
        }
}
