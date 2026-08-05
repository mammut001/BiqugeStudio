package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderFooterFormatTest {

    @Test
    fun batteryLabel_unknownAndCharging() {
        assertEquals("--%", ReaderFooterFormat.batteryLabel(-1, false))
        assertEquals("⚡--", ReaderFooterFormat.batteryLabel(-1, true))
    }

    @Test
    fun batteryLabel_clampsAndFormats() {
        assertEquals("0%", ReaderFooterFormat.batteryLabel(0, false))
        assertEquals("87%", ReaderFooterFormat.batteryLabel(87, false))
        assertEquals("100%", ReaderFooterFormat.batteryLabel(100, false))
        assertEquals("100%", ReaderFooterFormat.batteryLabel(150, false))
        assertEquals("⚡42%", ReaderFooterFormat.batteryLabel(42, true))
    }

    @Test
    fun batteryContentDescription_chinese() {
        assertTrue(ReaderFooterFormat.batteryContentDescription(50, false).contains("50"))
        assertTrue(ReaderFooterFormat.batteryContentDescription(50, true).contains("充电"))
        assertTrue(ReaderFooterFormat.batteryContentDescription(-1, false).contains("未知"))
    }
}
