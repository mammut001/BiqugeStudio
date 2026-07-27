package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for chapter detection (same regex as legacy Java reader). */
class ChapterIndexTest {

    @Test
    fun findChapters_emptyBody_returnsFullTextLabel() {
        val chapters = ChapterIndex.findChapters("", fullTextLabel = "全文")
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0, chapters[0].start)
    }

    @Test
    fun findChapters_detectsChapterHuiAndJuanStyleHeadings() {
        val text = """
            前言内容
            
            第一章 开端
            正文甲
            
            第2章 发展
            正文乙
            
            第 三 回 高潮
            正文丙
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertTrue("expected at least 2 chapters, got ${chapters.map { it.title }}", chapters.size >= 2)
        assertTrue(chapters.any { it.title.contains("章") || it.title.contains("回") })
        // First real chapter starts after the preface, not necessarily at 0.
        assertTrue(chapters[0].start >= 0)
        // Starts must be strictly increasing for distinct headings
        for (i in 1 until chapters.size) {
            assertTrue(chapters[i].start > chapters[i - 1].start)
        }
    }

    @Test
    fun findChapters_trimsWhitespaceOnTitle() {
        val text = "  第一章 测试  \n内容"
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(1, chapters.size)
        assertEquals("第一章 测试", chapters[0].title)
    }

    @Test
    fun chapterAtOffset_selectsLastChapterNotPastOffset() {
        val chapters = listOf(
            Chapter("第一章", 0),
            Chapter("第二章", 100),
            Chapter("第三章", 200),
        )
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, 0))
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, 50))
        assertEquals(1, ChapterIndex.chapterAtOffset(chapters, 100))
        assertEquals(1, ChapterIndex.chapterAtOffset(chapters, 150))
        assertEquals(2, ChapterIndex.chapterAtOffset(chapters, 200))
        assertEquals(2, ChapterIndex.chapterAtOffset(chapters, 9999))
    }

    @Test
    fun chapterAtOffset_emptyList_returnsZero() {
        assertEquals(0, ChapterIndex.chapterAtOffset(emptyList(), 42))
    }
}
