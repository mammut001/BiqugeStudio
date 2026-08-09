package app.maoyankanshu.novel.selfuse.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CoverBitmapTest {

    @Test
    fun calculateInSampleSize_downsamplesByPowerOfTwo() {
        assertEquals(1, CoverBitmap.calculateInSampleSize(100, 100, 100, 100))
        assertEquals(4, CoverBitmap.calculateInSampleSize(400, 400, 100, 100))
        assertEquals(8, CoverBitmap.calculateInSampleSize(800, 800, 100, 100))
    }

    @Test
    fun calculateInSampleSize_invalidDimensionsStaySafe() {
        assertEquals(1, CoverBitmap.calculateInSampleSize(0, 800, 100, 100))
        assertEquals(1, CoverBitmap.calculateInSampleSize(800, 800, 0, 100))
    }

    @Test
    fun cacheKey_changesWhenFileOrRequestedSizeChanges() {
        val base = CoverBitmap.coverCacheKey("/books/a.jpg", 10L, 20L, 56, 74)
        assertEquals(base, CoverBitmap.coverCacheKey("/books/a.jpg", 10L, 20L, 56, 74))
        assertNotEquals(base, CoverBitmap.coverCacheKey("/books/a.jpg", 11L, 20L, 56, 74))
        assertNotEquals(base, CoverBitmap.coverCacheKey("/books/a.jpg", 10L, 21L, 56, 74))
        assertNotEquals(base, CoverBitmap.coverCacheKey("/books/a.jpg", 10L, 20L, 112, 148))
    }
}
