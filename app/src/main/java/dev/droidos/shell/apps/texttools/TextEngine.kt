package dev.droidos.shell.apps.texttools

/**
 * Text statistics and transforms. Pure Kotlin with no Android imports, so every case here is
 * unit-tested rather than checked by eye on a device.
 */
object TextEngine {

    data class Stats(
        val characters: Int,
        val withoutSpaces: Int,
        val words: Int,
        val lines: Int,
        val sentences: Int
    )

    enum class Transform(val label: String) {
        UPPER("UPPER"),
        LOWER("lower"),
        TITLE("Title Case"),
        SENTENCE("Sentence case"),
        REVERSED("Reversed"),
        SLUG("slug-ified"),
        SORTED_LINES("Sort lines"),
        UNIQUE_LINES("Unique lines")
    }

    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_END = Regex("[.!?]+")
    private val NOT_SLUG = Regex("[^a-z0-9]+")

    fun stats(text: String): Stats {
        val trimmed = text.trim()
        return Stats(
            characters = text.length,
            withoutSpaces = text.count { !it.isWhitespace() },
            words = if (trimmed.isEmpty()) 0 else trimmed.split(WHITESPACE).count { it.isNotEmpty() },
            lines = if (text.isEmpty()) 0 else text.lines().size,
            sentences = if (trimmed.isEmpty()) 0 else text.split(SENTENCE_END).count { it.isNotBlank() }
        )
    }

    fun apply(text: String, transform: Transform): String = when (transform) {
        Transform.UPPER -> text.uppercase()
        Transform.LOWER -> text.lowercase()
        Transform.TITLE -> text.split(WHITESPACE).joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.titlecase() }
        }.trim()
        Transform.SENTENCE -> text.lowercase()
            .replaceFirstChar { it.titlecase() }
            .replace(Regex("(?<=[.!?]\\s)([a-z])")) { it.value.uppercase() }
        Transform.REVERSED -> text.reversed()
        Transform.SLUG -> text.lowercase()
            .replace(NOT_SLUG, "-")
            .trim('-')
        Transform.SORTED_LINES -> text.lines().sorted().joinToString("\n")
        Transform.UNIQUE_LINES -> text.lines().distinct().joinToString("\n")
    }

    sealed interface ReplaceResult {
        data class Ok(val value: String, val replacements: Int) : ReplaceResult
        data class Failed(val reason: String) : ReplaceResult
    }

    /** Runs a real regex, surfacing an invalid pattern as an error instead of silently skipping. */
    fun replace(text: String, pattern: String, replacement: String): ReplaceResult {
        if (pattern.isEmpty()) return ReplaceResult.Failed("enter a pattern")
        return try {
            val regex = Regex(pattern)
            val count = regex.findAll(text).count()
            ReplaceResult.Ok(regex.replace(text, replacement), count)
        } catch (e: Exception) {
            ReplaceResult.Failed(e.message ?: "invalid pattern")
        }
    }
}
