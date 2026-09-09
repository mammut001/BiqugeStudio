package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsSleepTimerTest {
    @Test
    fun clampAndEnable() {
        assertEquals(0, TtsSleepTimer.clampMin(-5))
        assertEquals(180, TtsSleepTimer.clampMin(999))
        assertFalse(TtsSleepTimer.isEnabled(0))
        assertTrue(TtsSleepTimer.isEnabled(15))
    }

    @Test
    fun delayAndLabel() {
        assertEquals(0L, TtsSleepTimer.delayMs(0))
        assertEquals(15 * 60_000L, TtsSleepTimer.delayMs(15))
        assertEquals("关", TtsSleepTimer.label(0))
        assertEquals("30分钟", TtsSleepTimer.label(30))
    }

    @Test
    fun presetSelected() {
        assertTrue(TtsSleepTimer.isPresetSelected(30, 30))
        assertFalse(TtsSleepTimer.isPresetSelected(30, 60))
        assertTrue(TtsSleepTimer.isPresetSelected(0, 0))
    }
}
