package dev.droidos.shell.apps.terminal

import android.content.Context
import dev.droidos.shell.core.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.InputStream

/**
 * Runs real commands through `/system/bin/sh` as this application's UID.
 *
 * Nothing is simulated: output is whatever the process printed, stderr is kept separate, and the
 * exit code is the process's own. SELinux confines the shell to the app's domain, so commands that
 * touch other apps' data or system settings fail with the kernel's message - those failures are
 * shown, not hidden. There is no root escalation here.
 */
class TerminalSession(private val context: Context) {

    enum class Kind { INPUT, OUTPUT, ERROR, SYSTEM }

    data class Line(val text: String, val kind: Kind)

    private val _lines = MutableStateFlow<List<Line>>(emptyList())
    val lines: StateFlow<List<Line>> = _lines.asStateFlow()

    private val _cwd = MutableStateFlow(defaultDirectory())
    val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var process: Process? = null
    private val history = ArrayList<String>()

    val home: String get() = defaultDirectory()

    init {
        append(
            "DroidOS terminal · uid ${android.os.Process.myUid()} · ${TerminalEngine.SHELL}",
            Kind.SYSTEM
        )
        append("Type 'help' for builtins. Tapping Back leaves the session running.", Kind.SYSTEM)
    }

    private fun defaultDirectory(): String {
        val files = context.filesDir
        return if (files != null && files.isDirectory) files.absolutePath else "/"
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun append(text: String, kind: Kind) {
        val next = _lines.value + Line(text, kind)
        _lines.value = if (next.size > MAX_LINES) next.takeLast(MAX_LINES) else next
    }

    private fun help() {
        append(
            "builtins: clear, help, exit, cd <dir>  ·  everything else runs through ${TerminalEngine.SHELL}",
            Kind.SYSTEM
        )
        append(
            "The shell runs as this app's UID inside the app sandbox, so commands that need system " +
                "rights fail with a permission error from the kernel rather than pretending to work.",
            Kind.SYSTEM
        )
    }

    suspend fun run(rawLine: String) {
        val line = rawLine.trim()
        if (line.isEmpty() || _running.value) return

        append("${TerminalEngine.prompt(_cwd.value, home)} $line", Kind.INPUT)
        if (history.lastOrNull() != line) history.add(line)

        TerminalEngine.builtin(line)?.let { builtin ->
            when (builtin) {
                TerminalEngine.Builtin.CLEAR -> clear()
                TerminalEngine.Builtin.HELP -> help()
                TerminalEngine.Builtin.EXIT ->
                    append("Back closes the terminal; this app has no parent shell to exit.", Kind.SYSTEM)
            }
            return
        }

        if (TerminalEngine.isCd(line)) {
            val target = TerminalEngine.resolvePath(TerminalEngine.cdArgument(line), _cwd.value, home)
            val directory = File(target)
            if (directory.isDirectory && directory.canRead()) {
                _cwd.value = target
                append(target, Kind.SYSTEM)
            } else {
                append("cd: $target: ${if (directory.exists()) "not readable" else "no such directory"}", Kind.ERROR)
            }
            return
        }

        execute(TerminalEngine.shellArgs(line, _cwd.value))
    }

    fun cancel() {
        val runningProcess = process
        if (runningProcess == null) {
            append("nothing to cancel", Kind.SYSTEM)
            return
        }
        runningProcess.destroy()
        append("process terminated by you", Kind.ERROR)
    }

    private suspend fun execute(args: List<String>) = withContext(Dispatchers.IO) {
        _running.value = true
        val started = try {
            ProcessBuilder(args).apply { redirectErrorStream(false) }.start()
        } catch (e: Exception) {
            _running.value = false
            AppLog.e("Terminal", "Could not start ${args.first()}", e)
            append("cannot start ${args.first()}: ${e.message}", Kind.ERROR)
            return@withContext
        }
        process = started

        try {
            coroutineScope {
                val stdout = launch(Dispatchers.IO) { drain(started.inputStream, Kind.OUTPUT) }
                val stderr = launch(Dispatchers.IO) { drain(started.errorStream, Kind.ERROR) }

                val finished = withTimeoutOrNull(TIMEOUT_MS) { started.waitFor() }
                if (finished == null) {
                    started.destroy()
                    append("timed out after ${TIMEOUT_MS / 1000}s; process terminated", Kind.ERROR)
                }
                stdout.join()
                stderr.join()
            }
            val code = runCatching { started.exitValue() }.getOrDefault(-1)
            append(TerminalEngine.exitLabel(code), Kind.SYSTEM)
        } finally {
            process = null
            _running.value = false
        }
    }

    private fun drain(stream: InputStream, kind: Kind) {
        try {
            stream.bufferedReader().useLines { lines ->
                for (line in lines) append(line, kind)
            }
        } catch (e: Exception) {
            // A destroyed process closes its pipes; only report unexpected failures.
            if (process != null) AppLog.w("Terminal", "Stream ended with ${e.message}", e)
        }
    }

    private companion object {
        const val MAX_LINES = 600
        const val TIMEOUT_MS = 20_000L
    }
}
