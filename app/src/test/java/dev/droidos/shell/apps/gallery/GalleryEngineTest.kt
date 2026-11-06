package dev.droidos.shell.apps.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class GalleryEngineTest {

    private val utc = ZoneId.of("UTC")

    @Test
    fun columnsStayWithinTheGridLimits() {
        assertEquals(2, GalleryEngine.columns(120))
        assertEquals(3, GalleryEngine.columns(360))
        assertEquals(6, GalleryEngine.columns(1_400))
    }

    @Test
    fun onlyImagesPass() {
        assertTrue(GalleryEngine.isImage("image/jpeg"))
        assertFalse(GalleryEngine.isImage("video/mp4"))
        assertFalse(GalleryEngine.isImage(null))
    }

    @Test
    fun datesFallBackWhenMissing() {
        assertEquals("6 Nov 2026", GalleryEngine.takenLabel(1_793_923_200, utc))
        assertEquals("No capture date", GalleryEngine.takenLabel(0, utc))
        assertEquals("6 Nov 2026, 00:00", GalleryEngine.takenLabelWithTime(1_793_923_200, utc))
    }

    @Test
    fun positionsAreClamped() {
        assertEquals("1 of 4", GalleryEngine.positionLabel(0, 4))
        assertEquals("4 of 4", GalleryEngine.positionLabel(9, 4))
        assertEquals("0 of 0", GalleryEngine.positionLabel(0, 0))
    }

    @Test
    fun sizesReadNaturally() {
        assertEquals("512 B", GalleryEngine.byteSize(512))
        assertEquals("2 KB", GalleryEngine.byteSize(2_048))
        assertEquals("1.5 MB", GalleryEngine.byteSize(1_572_864))
        assertEquals("2.00 GB", GalleryEngine.byteSize(2_147_483_648))
        assertEquals("unknown size", GalleryEngine.byteSize(0))
    }

    @Test
    fun foldersComeFromTheRelativePath() {
        assertEquals("Camera", GalleryEngine.folderOf("DCIM/Camera/frame.jpg"))
        assertEquals("All images", GalleryEngine.folderOf("frame.jpg"))
        assertEquals("All images", GalleryEngine.folderOf(null))
    }

    @Test
    fun samplingFollowsThePixelBudget() {
        assertEquals(1, GalleryEngine.sampleSize(100, 100, 256))
        assertEquals(8, GalleryEngine.sampleSize(2048, 2048, 256))
        assertEquals(1, GalleryEngine.sampleSize(0, 10, 256))
    }
}
