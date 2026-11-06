package dev.droidos.shell.apps.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MusicEngineTest {

    private val tracks = listOf(
        Track(1, "Beta", "Artist B", "Album B", 187_000),
        Track(2, "alpha", "Artist A", "Album A", 59_000),
        Track(3, "Gamma", "Artist C", "Album A", 3_727_000)
    )

    @Test
    fun formatsDurations() {
        assertEquals("0:00", MusicEngine.formatDuration(0))
        assertEquals("0:59", MusicEngine.formatDuration(59_000))
        assertEquals("3:07", MusicEngine.formatDuration(187_000))
        assertEquals("1:02:07", MusicEngine.formatDuration(3_727_000))
    }

    @Test
    fun filtersAcrossTitleArtistAndAlbum() {
        assertEquals(listOf(2L), MusicEngine.filter(tracks, "alpha").map { it.id })
        assertEquals(listOf(2L, 3L), MusicEngine.filter(tracks, "album a").map { it.id })
        assertEquals(1, MusicEngine.filter(tracks, "artist c").size)
        assertEquals(3, MusicEngine.filter(tracks, "   ").size)
        assertEquals(0, MusicEngine.filter(tracks, "zzz").size)
    }

    @Test
    fun sortsTitlesCaseInsensitively() {
        assertEquals(listOf("alpha", "Beta", "Gamma"), MusicEngine.sortByTitle(tracks).map { it.title })
    }

    @Test
    fun advanceMovesForwardAndStopsAtTheEnd() {
        assertEquals(1, MusicEngine.advance(0, 3, false, RepeatMode.OFF, automatic = true))
        assertEquals(2, MusicEngine.advance(1, 3, false, RepeatMode.OFF, automatic = true))
        assertEquals(-1, MusicEngine.advance(2, 3, false, RepeatMode.OFF, automatic = true))
        assertEquals(2, MusicEngine.advance(2, 3, false, RepeatMode.OFF, automatic = false))
        assertEquals(0, MusicEngine.advance(2, 3, false, RepeatMode.ALL, automatic = true))
        assertEquals(1, MusicEngine.advance(1, 3, false, RepeatMode.ONE, automatic = true))
        assertEquals(0, MusicEngine.advance(2, 3, false, RepeatMode.ONE, automatic = false))
        assertEquals(-1, MusicEngine.advance(0, 0, false, RepeatMode.ALL, automatic = true))
    }

    @Test
    fun shuffleStaysInBoundsAndMoves() {
        repeat(20) { seed ->
            val next = MusicEngine.advance(
                current = 1,
                size = 4,
                shuffle = true,
                repeat = RepeatMode.ALL,
                automatic = true,
                random = Random(seed)
            )
            assertTrue(next in 0..3)
            assertNotEquals(1, next)
        }
    }

    @Test
    fun rewindWrapsOnlyWithRepeatAll() {
        assertEquals(1, MusicEngine.rewind(2, 3, false, RepeatMode.ALL))
        assertEquals(2, MusicEngine.rewind(0, 3, false, RepeatMode.ALL))
        assertEquals(0, MusicEngine.rewind(0, 3, false, RepeatMode.OFF))
        assertEquals(0, MusicEngine.rewind(1, 3, false, RepeatMode.OFF))
    }

    @Test
    fun progressIsClamped() {
        assertEquals(0.5f, MusicEngine.progress(1_000, 2_000), 1e-6f)
        assertEquals(0f, MusicEngine.progress(1_000, 0), 1e-6f)
        assertEquals(1f, MusicEngine.progress(5_000, 2_000), 1e-6f)
    }

    @Test
    fun repeatCyclesThroughThreeStates() {
        assertEquals(RepeatMode.ALL, MusicEngine.nextRepeatMode(RepeatMode.OFF))
        assertEquals(RepeatMode.ONE, MusicEngine.nextRepeatMode(RepeatMode.ALL))
        assertEquals(RepeatMode.OFF, MusicEngine.nextRepeatMode(RepeatMode.ONE))
    }
}
