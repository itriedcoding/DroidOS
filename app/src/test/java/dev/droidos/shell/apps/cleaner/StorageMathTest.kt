package dev.droidos.shell.apps.cleaner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageMathTest {

    @Test
    fun formatsBinaryUnits() {
        assertEquals("0 B", StorageMath.formatBytes(0L))
        assertEquals("512 B", StorageMath.formatBytes(512L))
        assertEquals("1.0 KB", StorageMath.formatBytes(1024L))
        assertEquals("1.5 MB", StorageMath.formatBytes(1024L * 1024L + 512L * 1024L))
        assertEquals("2.0 GB", StorageMath.formatBytes(2L * 1024L * 1024L * 1024L))
    }

    @Test
    fun negativeSizesReadAsZero() {
        assertEquals("0 B", StorageMath.formatBytes(-4096L))
    }

    @Test
    fun percentagesClampAndAvoidDivisionByZero() {
        assertEquals(0.25f, StorageMath.percentUsed(50L, 200L), 1e-6f)
        assertEquals("25%", StorageMath.percentLabel(50L, 200L))
        assertEquals(0f, StorageMath.percentUsed(10L, 0L), 1e-6f)
        assertEquals("--", StorageMath.percentLabel(10L, 0L))
        assertEquals(1f, StorageMath.percentUsed(300L, 200L), 1e-6f)
    }

    @Test
    fun ranksBiggestFirstAndDropsEmpties() {
        val buckets = listOf(
            SizeBucket("cache", 2048L, 4),
            SizeBucket("code", 0L, 0),
            SizeBucket("files", 8192L, 2)
        )
        val ranked = StorageMath.rank(buckets)
        assertEquals(listOf("files", "cache"), ranked.map { it.label })
        assertEquals(10_240L, StorageMath.total(buckets))
    }

    @Test
    fun rankHonoursLimit() {
        val buckets = (1..20).map { SizeBucket("b$it", it.toLong(), 1) }
        assertEquals(5, StorageMath.rank(buckets, limit = 5).size)
        assertEquals("b20", StorageMath.rank(buckets, limit = 5).first().label)
    }

    @Test
    fun freedMessageDescribesTheOutcome() {
        assertTrue(StorageMath.freedMessage(0L, 0).contains("already empty"))
        assertTrue(StorageMath.freedMessage(2048L, 3).contains("2.0 KB"))
        assertTrue(StorageMath.freedMessage(2048L, 3).contains("3 files"))
        assertTrue(StorageMath.freedMessage(0L, 2).contains("no space was reclaimed"))
        assertTrue(StorageMath.freedMessage(1024L, 1).endsWith("1 file"))
    }

    @Test
    fun shareOfGuardsAgainstZeroTotals() {
        assertEquals(0.5f, StorageMath.shareOf(5L, 10L), 1e-6f)
        assertEquals(0f, StorageMath.shareOf(5L, 0L), 1e-6f)
    }
}
