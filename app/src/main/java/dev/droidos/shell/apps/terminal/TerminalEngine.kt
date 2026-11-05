package dev.droidos.shell.apps.terminal

/**
 * Command parsing for the terminal. Pure: no Android imports, no process handling, so the path
 * maths, quoting and builtin detection are unit-tested in `TerminalEngineTest`.
 *
 * The actual process execution lives in [TerminalSession].
 */
object TerminalEngine {

    /** The shell every Android device ships. Nothing is installed or bundled for this. */
    const val SHELL = "/system/bin/sh"

    enum class Builtin { CLEAR, HELP, EXIT }

    fun builtin(line: String): Builtin? = when (line.trim().lowercase()) {
        "clear", "cls" -> Builtin.CLEAR
        "help" -> Builtin.HELP
        "exit" -> Builtin.EXIT
        else -> null
    }

    /** POSIX single-quote escaping: a quote inside the value becomes '\'' . */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    /**
     * Wraps a command so it runs exactly in [cwd]. Joined with `&&` so a directory that vanished
     * makes the command fail instead of silently running somewhere else.
     */
    fun script(line: String, cwd: String): String = "cd ${quote(cwd)} && ( ${line.trim()} )"

    fun shellArgs(line: String, cwd: String): List<String> = listOf(SHELL, "-c", script(line, cwd))

    fun isCd(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed == "cd" || trimmed.startsWith("cd ")
    }

    fun cdArgument(line: String): String = line.trim().removePrefix("cd").trim()

    /**
     * Resolves a `cd` argument against [cwd] without touching the filesystem. The caller decides
     * whether the result is a readable directory, because guessing here would hide a real error.
     */
    fun resolvePath(target: String, cwd: String, home: String): String {
        val raw = target.trim().ifEmpty { home }
        val expanded = when {
            raw == "~" -> home
            raw.startsWith("~/") -> home + raw.removePrefix("~")
            raw.startsWith("/") -> raw
            else -> "$cwd/$raw"
        }
        return normalize(expanded)
    }

    /** Collapses `.`, `..` and duplicate slashes. */
    fun normalize(path: String): String {
        val absolute = path.startsWith("/")
        val segments = ArrayList<String>()
        for (segment in path.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isNotEmpty()) segments.removeAt(segments.size - 1)
                else -> segments.add(segment)
            }
        }
        val joined = segments.joinToString("/")
        return if (absolute) "/$joined" else joined
    }

    /** Short prompt form: `~` when at home, otherwise the real path. */
    fun prompt(cwd: String, home: String): String = if (cwd == home) "~" else cwd

    fun exitLabel(code: Int): String = "exit $code"
}
