package dev.droidos.shell.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures uncaught exceptions to an on-disk report before the process dies, so a crash
 * is diagnosable from the Diagnostics screen on the next launch instead of vanishing.
 */
object CrashGuard {

    private const val MAX_REPORTS = 5
    private var crashDirectory: File? = null
    private var installed = false

    fun install(filesDir: File) {
        if (installed) return
        crashDirectory = File(filesDir, "crashes").apply { mkdirs() }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { persist(thread, throwable) }
            AppLog.e("CrashGuard", "Uncaught exception on ${thread.name}", throwable)
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }

    private fun persist(thread: Thread, throwable: Throwable) {
        val dir = crashDirectory ?: return
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "crash-$stamp.txt")
        file.writeText(
            buildString {
                append("DroidOS crash report\n")
                append("time: ${Date()}\n")
                append("thread: ${thread.name}\n")
                append("version: ${BuildInfo.VERSION_NAME} (${BuildInfo.VERSION_CODE})\n")
                append("device: ${BuildInfo.DEVICE_SUMMARY}\n\n")
                append("log tail:\n")
                append(AppLog.exportText())
                append("\n\nstack trace:\n")
                append(throwable.stackTraceToString())
            }
        )
        // Keep only the newest reports so storage cannot grow without bound.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_REPORTS)?.forEach { it.delete() }
    }

    fun listReports(): List<File> {
        val dir = crashDirectory ?: return emptyList()
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
