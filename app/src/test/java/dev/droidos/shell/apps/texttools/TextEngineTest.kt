package dev.droidos.shell.apps.texttools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEngineTest {

    @Test
    fun statisticsCountRealCharactersWordsLinesAndSentences() {
        val stats = TextEngine.stats("Hello world.\nSecond line!")
        assertEquals(25, stats.characters)
        assertEquals(22, stats.withoutSpaces)
        assertEquals(4, stats.words)
        assertEquals(2, stats.lines)
        assertEquals(2, stats.sentences)
    }

    @Test
    fun emptyTextHasNoStatistics() {
        val stats = TextEngine.stats("")
        assertEquals(0, stats.characters)
        assertEquals(0, stats.words)
        assertEquals(0, stats.lines)
        assertEquals(0, stats.sentences)
    }

    @Test
    fun caseTransformsBehaveAsLabelled() {
        assertEquals("HELLO WORLD", TextEngine.apply("hello world", TextEngine.Transform.UPPER))
        assertEquals("hello world", TextEngine.apply("hello WORLD", TextEngine.Transform.LOWER))
        assertEquals("Hello World", TextEngine.apply("hello WORLD", TextEngine.Transform.TITLE))
        assertEquals(
            "Hello world. Second line!",
            TextEngine.apply("hello world. second line!", TextEngine.Transform.SENTENCE)
        )
    }

    @Test
    fun slugAndLineTransformsAreDeterministic() {
        assertEquals("hello-world", TextEngine.apply("Hello, World!", TextEngine.Transform.SLUG))
        assertEquals("a\nb", TextEngine.apply("b\na", TextEngine.Transform.SORTED_LINES))
        assertEquals("a\nb", TextEngine.apply("a\nb\na", TextEngine.Transform.UNIQUE_LINES))
        assertEquals("cba", TextEngine.apply("abc", TextEngine.Transform.REVERSED))
    }

    @Test
    fun regexReplaceReportsMatchesAndGroupReferences() {
        val result = TextEngine.replace("a1 b2", "\\d", "#")
        assertTrue(result is TextEngine.ReplaceResult.Ok)
        result as TextEngine.ReplaceResult.Ok
        assertEquals("a# b#", result.value)
        assertEquals(2, result.replacements)

        val grouped = TextEngine.replace("Doe, Jane", "(\\w+), (\\w+)", "\$2 \$1")
        assertEquals("Jane Doe", (grouped as TextEngine.ReplaceResult.Ok).value)
    }

    @Test
    fun invalidPatternIsAnErrorNotAnEmptyResult() {
        val result = TextEngine.replace("text", "[", "x")
        assertTrue(result is TextEngine.ReplaceResult.Failed)
        assertTrue(TextEngine.replace("text", "", "x") is TextEngine.ReplaceResult.Failed)
    }
}
