package app.maoyankanshu.novel.selfuse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure JVM tests for [CoverBitmap.calculateInSampleSize] (no BitmapFactory / device).
 */
class CoverBitmapSampleSizeTest {

    @Test
    fun fitsTile_returnsOne() {
        assertEquals(1, CoverBitmap.calculateInSampleSize(56, 74, 56, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(40, 50, 56, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(1, 1, 56, 74))
    }

    @Test
    fun doubleTile_returnsTwo() {
        // Source ≥ 2× req on both axes → sample 2
        assertEquals(2, CoverBitmap.calculateInSampleSize(112, 148, 56, 74))
        assertEquals(2, CoverBitmap.calculateInSampleSize(200, 200, 56, 74))
    }

    @Test
    fun largeCover_powersOfTwo() {
        // 896×1184 vs 56×74: loop continues while half/sample still ≥ req → sample 16
        assertEquals(16, CoverBitmap.calculateInSampleSize(896, 1184, 56, 74))
        // Large source vs ~2× density tile
        assertEquals(16, CoverBitmap.calculateInSampleSize(2000, 3000, 112, 148))
    }

    @Test
    fun onlyOneAxisOversized_doesNotDownsamplePastFit() {
        // Width much larger but height already ≤ req → sample stays 1
        // (both axes must clear the half/sample threshold)
        assertEquals(1, CoverBitmap.calculateInSampleSize(400, 74, 56, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(56, 400, 56, 74))
    }

    @Test
    fun invalidDimensions_returnOne() {
        assertEquals(1, CoverBitmap.calculateInSampleSize(0, 100, 56, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(100, 0, 56, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(100, 100, 0, 74))
        assertEquals(1, CoverBitmap.calculateInSampleSize(100, 100, 56, 0))
        assertEquals(1, CoverBitmap.calculateInSampleSize(-1, -1, 56, 74))
    }
}
