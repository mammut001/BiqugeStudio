package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderCustomFontTest {
    @Test
    fun supportedNames_acceptTtfAndOtfCaseInsensitive() {
        assertTrue(ReaderCustomFont.isSupportedFontName("song.ttf"))
        assertTrue(ReaderCustomFont.isSupportedFontName("Kai.OTF"))
        assertFalse(ReaderCustomFont.isSupportedFontName("book.pdf"))
        assertFalse(ReaderCustomFont.isSupportedFontName("note.txt"))
        assertFalse(ReaderCustomFont.isSupportedFontName(""))
    }

    @Test
    fun ensureExtension_addsTtfOnlyWhenMissing() {
        assertEquals("a.ttf", ReaderCustomFont.ensureFontExtension("a.ttf"))
        assertEquals("b.otf", ReaderCustomFont.ensureFontExtension("b.otf"))
        assertEquals("c.ttf", ReaderCustomFont.ensureFontExtension("c"))
    }

    @Test
    fun sanitize_removesPathAndKeepsLengthBound() {
        assertEquals("song.ttf", ReaderCustomFont.sanitizeFileName("/sdcard/../song.ttf"))
        assertTrue(ReaderCustomFont.sanitizeFileName("a".repeat(200)).length <= 80)
    }
}
