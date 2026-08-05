package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPageTurnTest {

    @Test
    fun clampAndEnabled() {
        assertEquals(0, AutoPageTurn.clampSec(-5))
        assertEquals(300, AutoPageTurn.clampSec(999))
        assertFalse(AutoPageTurn.isEnabled(0))
        assertTrue(AutoPageTurn.isEnabled(20))
    }

    @Test
    fun delayMs_offIsZero() {
        assertEquals(0L, AutoPageTurn.delayMs(0))
        assertEquals(20_000L, AutoPageTurn.delayMs(20))
    }

    @Test
    fun labelsAndPresets() {
        assertEquals("关", AutoPageTurn.label(0))
        assertEquals("30s", AutoPageTurn.label(30))
        assertTrue(AutoPageTurn.isPresetSelected(20, 20))
        assertFalse(AutoPageTurn.isPresetSelected(20, 30))
        for (p in AutoPageTurn.PRESETS_SEC) {
            assertEquals(p, AutoPageTurn.clampSec(p))
        }
    }
}
