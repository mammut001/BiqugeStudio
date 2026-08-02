package app.maoyankanshu.novel.selfuse.ui.reader

import app.maoyankanshu.novel.selfuse.ReaderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for paper themes and font-family helpers used by the appearance sheet.
 */
class ReaderPaletteTest {

    @Test
    fun paperThemeOptions_coversAllPersistedIds() {
        val options = paperThemeOptions()
        assertTrue(options.size >= 8)
        val ids = options.map { it.id }.toSet()
        assertTrue(ids.contains(ReaderPreferences.THEME_PAPER))
        assertTrue(ids.contains(ReaderPreferences.THEME_NIGHT))
        assertTrue(ids.contains(ReaderPreferences.THEME_EYE_CARE))
        assertTrue(ids.contains(ReaderPreferences.THEME_WHITE))
        assertTrue(ids.contains(ReaderPreferences.THEME_GREEN))
        assertTrue(ids.contains(ReaderPreferences.THEME_PINK))
        assertTrue(ids.contains(ReaderPreferences.THEME_GRAY))
        assertTrue(ids.contains(ReaderPreferences.THEME_PARCHMENT))
        assertTrue(ids.contains(ReaderPreferences.THEME_SOFT_NIGHT))
        // Unique ids
        assertEquals(options.size, ids.size)
    }

    @Test
    fun readerPalette_nightAndPaperDiffer() {
        val paper = readerPalette(ReaderPreferences.THEME_PAPER)
        val night = readerPalette(ReaderPreferences.THEME_NIGHT)
        val green = readerPalette(ReaderPreferences.THEME_GREEN)
        assertNotEquals(paper.background, night.background)
        assertNotEquals(paper.background, green.background)
        // Unknown theme falls back to paper cream.
        assertEquals(paper.background, readerPalette(-1).background)
    }

    @Test
    fun clampReaderTheme_andFontFamily() {
        assertEquals(ReaderPreferences.THEME_PAPER, clampReaderTheme(-5))
        assertEquals(ReaderPreferences.THEME_SOFT_NIGHT, clampReaderTheme(ReaderPreferences.THEME_SOFT_NIGHT))
        assertEquals(ReaderPreferences.THEME_PAPER, clampReaderTheme(999))
        assertEquals(ReaderPreferences.FONT_SERIF, clampFontFamily(-1))
        assertEquals(ReaderPreferences.FONT_DEFAULT, clampFontFamily(99))
        assertEquals(ReaderPreferences.FONT_SANS, clampFontFamily(ReaderPreferences.FONT_SANS))
    }

    @Test
    fun readerFontFamily_resolvesWithoutCrash() {
        // Touch each path; equality of FontFamily instances is not stable across process.
        readerFontFamily(ReaderPreferences.FONT_SERIF)
        readerFontFamily(ReaderPreferences.FONT_SANS)
        readerFontFamily(ReaderPreferences.FONT_DEFAULT)
        readerFontFamily(99)
    }
}
