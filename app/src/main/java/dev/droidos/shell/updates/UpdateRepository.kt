package dev.droidos.shell.updates

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import dev.droidos.shell.core.AppLog
import dev.droidos.shell.core.BuildInfo
import dev.droidos.shell.core.Outcome
import dev.droidos.shell.core.attempt
import dev.droidos.shell.data.SettingsRepository
import dev.droidos.shell.security.CryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Secure update pipeline: fetch an HTTPS manifest, compare versions, download the APK,
 * verify its SHA-256 against the manifest AND that it is signed by the same certificate as
 * the running app, and only then hand it to the system installer.
 */
class UpdateRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {

    sealed interface CheckResult {
        data class Available(val manifest: UpdateManifest) : CheckResult
        data object UpToDate : CheckResult
        data class Failed(val reason: String) : CheckResult
    }

    sealed interface DownloadResult {
        data class Ready(val apk: File) : DownloadResult
        data class Failed(val reason: String) : DownloadResult
    }

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        val channel = settingsRepository.current().updateChannelUrl
            .ifBlank { BuildInfo.UPDATE_CHANNEL_URL }
        val json = try {
            httpGet(channel)
        } catch (e: Exception) {
            AppLog.e("Updates", "Manifest fetch failed for $channel", e)
            return@withContext CheckResult.Failed(e.message ?: "Could not reach the update channel")
        }

        val manifest = try {
            UpdateManifest.parse(json)
        } catch (e: Exception) {
            AppLog.e("Updates", "Manifest rejected", e)
            return@withContext CheckResult.Failed("Update manifest is invalid: ${e.message}")
        }

        settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
        val newer = manifest.versionCode > BuildInfo.VERSION_CODE ||
            Versions.compare(manifest.versionName, BuildInfo.VERSION_NAME) == VersionRelation.NEWER
        AppLog.i(
            "Updates",
            "Channel offers ${manifest.versionName} (${manifest.versionCode}); installed ${BuildInfo.VERSION_NAME}"
        )
        if (newer) CheckResult.Available(manifest) else CheckResult.UpToDate
    }

    suspend fun download(manifest: UpdateManifest): DownloadResult = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "updates/${manifest.versionName}.apk")
        target.parentFile?.mkdirs()
        try {
            val connection = (URL(manifest.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            connection.connect()
            if (connection.responseCode !in 200..299) {
                return@withContext DownloadResult.Failed("Server returned HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
        } catch (e: Exception) {
            AppLog.e("Updates", "Download failed", e)
            return@withContext DownloadResult.Failed(e.message ?: "Download failed")
        }

        val digest = CryptoManager(context).sha256Hex(target)
        if (!digest.equals(manifest.sha256, ignoreCase = true)) {
            target.delete()
            AppLog.e("Updates", "SHA-256 mismatch: expected ${manifest.sha256} got $digest")
            return@withContext DownloadResult.Failed("Package checksum did not match the manifest")
        }

        when (val signature = sameSigner(target)) {
            is Outcome.Err -> {
                target.delete()
                return@withContext DownloadResult.Failed(signature.message)
            }
            is Outcome.Ok -> AppLog.i("Updates", "Downloaded APK verified (${target.length()} bytes)")
        }
        DownloadResult.Ready(target)
    }

    /** Rejects any APK not signed with the identity of the currently installed app. */
    private fun sameSigner(apk: File): Outcome<Unit> = attempt("Verify APK signature") {
        val archive = context.packageManager.getPackageArchiveInfo(
            apk.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        ) ?: throw IllegalStateException("The downloaded file is not a valid APK")

        val archiveSigners = archive.signingInfo?.apkContentsSigners
            ?: throw IllegalStateException("Downloaded APK has no signing certificate")
        val installed = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        ).signingInfo?.apkContentsSigners
            ?: throw IllegalStateException("Installed app has no signing certificate")

        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest(installed[0].toByteArray())
        val actual = java.security.MessageDigest.getInstance("SHA-256")
            .digest(archiveSigners[0].toByteArray())

        if (!expected.contentEquals(actual)) {
            throw SecurityException("The update is signed by a different developer identity")
        }
        Unit
    }

    /** Hands the verified APK to the system installer (the user still confirms). */
    fun install(apk: File): Outcome<Unit> = attempt("Start installer") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun httpGet(url: String): String {
        require(url.startsWith("https://")) { "Update channel must use HTTPS" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Update channel returned HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
