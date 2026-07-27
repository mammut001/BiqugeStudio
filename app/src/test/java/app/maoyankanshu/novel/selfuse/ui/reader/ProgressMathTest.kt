package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMathTest {

    @Test
    fun scrollAndProgress_roundTripEndpoints() {
        val max = 1000
        assertEquals(0, ProgressMath.scrollYForProgress(0, max))
        assertEquals(max, ProgressMath.scrollYForProgress(1000, max))
        assertEquals(0, ProgressMath.progressForScrollY(0, max))
        assertEquals(1000, ProgressMath.progressForScrollY(max, max))
    }

    @Test
    fun progress_clampsAndHandlesZeroMax() {
        assertEquals(0, ProgressMath.scrollYForProgress(500, 0))
        assertEquals(0, ProgressMath.progressForScrollY(10, 0))
        assertEquals(0, ProgressMath.scrollYForProgress(-5, 200))
        assertEquals(200, ProgressMath.scrollYForProgress(2000, 200))
    }

    @Test
    fun midProgress_mapsNearHalfScroll() {
        val max = 800
        val y = ProgressMath.scrollYForProgress(500, max)
        assertEquals(400, y)
        assertEquals(500, ProgressMath.progressForScrollY(y, max))
    }

    @Test
    fun httpsOnly_acceptsHttpsRejectsHttpAndJunk() {
        assertTrue(ProgressMath.isHttpsUrl("https://example.com/a.epub"))
        assertTrue(ProgressMath.isHttpsUrl("  HTTPS://Example.COM/x  "))
        assertFalse(ProgressMath.isHttpsUrl("http://example.com/a.txt"))
        assertFalse(ProgressMath.isHttpsUrl("ftp://example.com/a"))
        assertFalse(ProgressMath.isHttpsUrl(""))
        assertFalse(ProgressMath.isHttpsUrl("not-a-url"))
    }
}
