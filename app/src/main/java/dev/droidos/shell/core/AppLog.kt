package dev.droidos.shell.core

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Structured logger: logcat for live debugging, a bounded in-memory ring buffer for the
 * Diagnostics screen, and a rotating on-disk trail so post-crash state is recoverable.
 * Writes never throw - logging must not be able to break a feature.
 */
object AppLog {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    data class Entry(
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: String? = null
    )

    private const val MAX_MEMORY_ENTRIES = 400
    private const val MAX_FILE_BYTES = 256L * 1024L
    private const val FILE_NAME = "droidos.log"

    private val lock = Any()
    private var logFile: File? = null

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun init(logDirectory: File) {
        synchronized(lock) {
            logDirectory.mkdirs()
            logFile = File(logDirectory, FILE_NAME)
        }
        i("AppLog", "Logging initialised at ${logDirectory.absolutePath}")
    }

    fun d(tag: String, message: String) = record(Level.DEBUG, tag, message, null)
    fun i(tag: String, message: String) = record(Level.INFO, tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = record(Level.WARN, tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = record(Level.ERROR, tag, message, error)

    private fun record(level: Level, tag: String, message: String, error: Throwable?) {
        val stack = error?.let { Log.getStackTraceString(it) }
        when (level) {
            Level.DEBUG -> Log.d(tag, message)
            Level.INFO -> Log.i(tag, message)
            Level.WARN -> Log.w(tag, message, error)
            Level.ERROR -> Log.e(tag, message, error)
        }

        val entry = Entry(System.currentTimeMillis(), level, tag, message, stack)
        synchronized(lock) {
            val next = (listOf(entry) + _entries.value).take(MAX_MEMORY_ENTRIES)
            _entries.value = next
        }
        if (level != Level.DEBUG) appendToFile(entry)
    }

    private fun appendToFile(entry: Entry) {
        val file = logFile ?: return
        try {
            synchronized(lock) {
                if (file.length() > MAX_FILE_BYTES) {
                    val trimmed = file.readText().takeLast((MAX_FILE_BYTES / 2).toInt())
                    file.writeText(trimmed)
                }
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(Date(entry.timestamp))
                file.appendText("$stamp ${entry.level} ${entry.tag}: ${entry.message}\n")
                entry.throwable?.let { file.appendText("$it\n") }
            }
        } catch (e: Exception) {
            Log.w("AppLog", "Could not append to log file", e)
        }
    }

    fun exportText(): String {
        val header = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = _entries.value.joinToString("\n") { entry ->
            val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
            buildString {
                append("$stamp ${entry.level} ${entry.tag}: ${entry.message}")
                entry.throwable?.let { append("\n").append(it) }
            }
        }
        return "DroidOS diagnostics - exported $header\n\n$body\n"
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
            runCatching { logFile?.writeText("") }
        }
    }
}
