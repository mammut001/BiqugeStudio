package app.maoyankanshu.novel.selfuse.ui.reader

import app.maoyankanshu.novel.selfuse.ReaderPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderReadingPolishTest {

    @Test
    fun isNightHour_overnightWindow() {
        // 19:00–07:00
        assertTrue(ReaderReadingPolish.isNightHour(19, 19, 7))
        assertTrue(ReaderReadingPolish.isNightHour(23, 19, 7))
        assertTrue(ReaderReadingPolish.isNightHour(0, 19, 7))
        assertTrue(ReaderReadingPolish.isNightHour(6, 19, 7))
        assertFalse(ReaderReadingPolish.isNightHour(7, 19, 7))
        assertFalse(ReaderReadingPolish.isNightHour(12, 19, 7))
        assertFalse(ReaderReadingPolish.isNightHour(18, 19, 7))
    }

    @Test
    fun isNightHour_sameStartEnd_never() {
        assertFalse(ReaderReadingPolish.isNightHour(0, 10, 10))
        assertFalse(ReaderReadingPolish.isNightHour(10, 10, 10))
    }

    @Test
    fun resolveEffectiveTheme_autoOffUsesManual() {
        assertEquals(
            ReaderPreferences.THEME_GREEN,
            ReaderReadingPolish.resolveEffectiveTheme(
                autoNightEnabled = false,
                hourOfDay = 22,
                manualTheme = ReaderPreferences.THEME_GREEN,
                dayTheme = ReaderPreferences.THEME_PAPER,
                nightTheme = ReaderPreferences.THEME_NIGHT,
            ),
        )
    }

    @Test
    fun resolveEffectiveTheme_autoOnSwitchesByHour() {
        assertEquals(
            ReaderPreferences.THEME_NIGHT,
            ReaderReadingPolish.resolveEffectiveTheme(
                autoNightEnabled = true,
                hourOfDay = 21,
                manualTheme = ReaderPreferences.THEME_PAPER,
                dayTheme = ReaderPreferences.THEME_WHITE,
                nightTheme = ReaderPreferences.THEME_NIGHT,
            ),
        )
        assertEquals(
            ReaderPreferences.THEME_WHITE,
            ReaderReadingPolish.resolveEffectiveTheme(
                autoNightEnabled = true,
                hourOfDay = 10,
                manualTheme = ReaderPreferences.THEME_NIGHT,
                dayTheme = ReaderPreferences.THEME_WHITE,
                nightTheme = ReaderPreferences.THEME_NIGHT,
            ),
        )
        assertEquals(
            ReaderPreferences.THEME_SOFT_NIGHT,
            ReaderReadingPolish.resolveEffectiveTheme(
                autoNightEnabled = true,
                hourOfDay = 23,
                manualTheme = ReaderPreferences.THEME_PAPER,
                dayTheme = ReaderPreferences.THEME_PAPER,
                nightTheme = ReaderPreferences.THEME_SOFT_NIGHT,
            ),
        )
    }

    @Test
    fun withParagraphFirstLineIndent_indentsNonEmptyLines() {
        val raw = "第一段开头\n\n第二段\n  已有空格"
        val out = ReaderReadingPolish.withParagraphFirstLineIndent(raw, enabled = true)
        assertTrue(out.startsWith("\u3000\u3000第一段"))
        assertTrue(out.contains("\n\n\u3000\u3000第二段"))
        // Idempotent-ish on fullwidth indent at line start
        val again = ReaderReadingPolish.withParagraphFirstLineIndent(out, enabled = true)
        assertEquals(out, again)
        assertEquals(raw, ReaderReadingPolish.withParagraphFirstLineIndent(raw, enabled = false))
        assertEquals("", ReaderReadingPolish.withParagraphFirstLineIndent("", true))
    }

    @Test
    fun pageTurnDurationMs() {
        assertEquals(280, ReaderReadingPolish.pageTurnDurationMs(true))
        assertEquals(0, ReaderReadingPolish.pageTurnDurationMs(false))
    }
}

class PageTurnEffectAnimationFlagTest {
    @Test
    fun disabledAnimation_isIdentity() {
        val t = PageTurnEffect.transform(0.8f, animationEnabled = false)
        assertEquals(0f, t.rotationY, 0.001f)
        assertEquals(1f, t.alpha, 0.001f)
        assertEquals(1f, t.scale, 0.001f)
    }

    @Test
    fun enabledAnimation_stillTilts() {
        val t = PageTurnEffect.transform(0.5f, animationEnabled = true)
        assertTrue(t.rotationY > 0f)
    }
}

class ReaderCustomFontNameTest {
    @Test
    fun sanitizeAndExtension() {
        assertEquals("My_Font.ttf", ReaderCustomFont.sanitizeFileName("My Font.ttf"))
        assertEquals("custom.ttf", ReaderCustomFont.ensureFontExtension("custom"))
        assertEquals("a.otf", ReaderCustomFont.ensureFontExtension("a.otf"))
        assertTrue(ReaderCustomFont.isSupportedFontName("x.TTF"))
        assertFalse(ReaderCustomFont.isSupportedFontName("x.txt"))
    }
}
