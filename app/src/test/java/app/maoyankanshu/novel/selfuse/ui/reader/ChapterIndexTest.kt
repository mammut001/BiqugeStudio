package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM unit tests for chapter detection (TXT/EPUB plain text, minSdk 23 safe). */
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
            
            第1卷 上
            正文丁
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertTrue("expected ≥3 chapters, got ${chapters.map { it.title }}", chapters.size >= 3)
        assertTrue(chapters.any { it.title.contains("章") || it.title.contains("回") })
        assertTrue(chapters.any { it.title.contains("卷") })
        assertStrictlyIncreasingStarts(chapters)
    }

    @Test
    fun findChapters_englishChapterAndCaseInsensitive() {
        val text = """
            Intro line
            
            Chapter 1 Opening
            body a
            
            CHAPTER 2 Rising
            body b
            
            chapter 3 Climax
            body c
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(3, chapters.size)
        assertTrue(chapters[0].title.startsWith("Chapter 1", ignoreCase = true))
        assertTrue(chapters[1].title.startsWith("CHAPTER 2", ignoreCase = true) ||
            chapters[1].title.startsWith("Chapter 2", ignoreCase = true))
        assertTrue(chapters[2].title.startsWith("chapter 3", ignoreCase = true))
        assertTrue(chapters[0].start > 0)
        assertStrictlyIncreasingStarts(chapters)
    }

    @Test
    fun findChapters_englishChapterRomanNumerals() {
        val text = """
            Preface blurb
            
            Chapter IV — The Gate
            body of four
            
            Chapter XII The Road
            body of twelve
            
            chapter ix lower roman
            body of nine
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(3, chapters.size)
        assertTrue(chapters[0].title.contains("Chapter IV", ignoreCase = true))
        assertTrue(chapters[0].title.contains("The Gate"))
        assertTrue(chapters[1].title.contains("XII", ignoreCase = true))
        assertTrue(chapters[2].title.contains("ix", ignoreCase = true))
        assertTrue(chapters[0].start > 0)
        assertStrictlyIncreasingStarts(chapters)
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, chapters[0].start))
        assertEquals(1, ChapterIndex.chapterAtOffset(chapters, chapters[1].start + 3))
        assertEquals(2, ChapterIndex.chapterAtOffset(chapters, chapters[2].start))
    }

    @Test
    fun findChapters_rejectsSentenceFalsePositives() {
        // Not a heading: "chapter" mid-sentence / no numeral; "Prologue is …" sentence.
        val text = """
            the chapter begins here
            and more text about chapters in general.
            Prologue is mentioned in this paragraph
            so we keep reading.
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text, fullTextLabel = "全文")
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0, chapters[0].start)
    }

    @Test
    fun findChapters_prologueAndEpilogue() {
        val text = """
            Prologue Dawn
            start
            
            Chapter 1 Mid
            middle
            
            Epilogue Dusk
            end
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(3, chapters.size)
        assertTrue(chapters[0].title.startsWith("Prologue", ignoreCase = true))
        assertTrue(chapters[1].title.startsWith("Chapter 1", ignoreCase = true))
        assertTrue(chapters[2].title.startsWith("Epilogue", ignoreCase = true))
        assertEquals(0, chapters[0].start)
        assertStrictlyIncreasingStarts(chapters)
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, 0))
        assertEquals(1, ChapterIndex.chapterAtOffset(chapters, chapters[1].start))
        assertEquals(2, ChapterIndex.chapterAtOffset(chapters, chapters[2].start + 1))
    }

    @Test
    fun findChapters_prologueAloneAndWithPunctuationTitle() {
        val text = """
            Prologue
            a
            
            Epilogue: The End
            b
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(2, chapters.size)
        assertEquals("Prologue", chapters[0].title)
        assertTrue(chapters[1].title.startsWith("Epilogue", ignoreCase = true))
    }

    @Test
    fun findChapters_chineseFrontAndBackMatter() {
        val text = """
            楔子 引子
            a
            
            第一章 正文
            b
            
            尾声 收束
            c
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(3, chapters.size)
        assertTrue(chapters[0].title.startsWith("楔子"))
        assertTrue(chapters[1].title.contains("第一章"))
        assertTrue(chapters[2].title.startsWith("尾声"))
        assertStrictlyIncreasingStarts(chapters)
    }

    @Test
    fun findChapters_mixedChineseAndEnglishEpubStyle() {
        val text = """
            Prologue
            intro
            
            第一章 相遇
            中文
            
            Chapter 2 Farewell
            english
            
            后记
            notes
        """.trimIndent()
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(4, chapters.size)
        assertEquals(0, chapters[0].start)
        assertStrictlyIncreasingStarts(chapters)
        // chapterAtOffset across mixed headings
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, chapters[0].start))
        assertEquals(1, ChapterIndex.chapterAtOffset(chapters, chapters[1].start + 2))
        assertEquals(2, ChapterIndex.chapterAtOffset(chapters, chapters[2].start))
        assertEquals(3, ChapterIndex.chapterAtOffset(chapters, chapters[3].start + 10))
    }

    @Test
    fun findChapters_trimsWhitespaceOnTitle() {
        val text = "  第一章 测试  \n内容"
        val chapters = ChapterIndex.findChapters(text)
        assertEquals(1, chapters.size)
        assertEquals("第一章 测试", chapters[0].title)
    }

    @Test
    fun findChapters_noHeading_returnsFullTextAtZero() {
        val text = "just a short paragraph without headings.\nline two."
        val chapters = ChapterIndex.findChapters(text, fullTextLabel = "全文")
        assertEquals(1, chapters.size)
        assertEquals("全文", chapters[0].title)
        assertEquals(0, chapters[0].start)
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

    @Test
    fun chapterAtOffset_negativeOffset_treatedAsZero() {
        val chapters = listOf(Chapter("A", 0), Chapter("B", 10))
        assertEquals(0, ChapterIndex.chapterAtOffset(chapters, -5))
    }

    @Test
    fun tocScrollIndex_clampsToCurrentChapter() {
        assertEquals(0, ChapterIndex.tocScrollIndex(0, 0))
        assertEquals(0, ChapterIndex.tocScrollIndex(5, 0))
        assertEquals(0, ChapterIndex.tocScrollIndex(-1, 10))
        assertEquals(0, ChapterIndex.tocScrollIndex(0, 10))
        assertEquals(7, ChapterIndex.tocScrollIndex(7, 10))
        assertEquals(9, ChapterIndex.tocScrollIndex(99, 10))
        // Mid-book open: TOC should scroll to reading chapter, not always 0.
        assertEquals(42, ChapterIndex.tocScrollIndex(42, 200))
    }

    private fun assertStrictlyIncreasingStarts(chapters: List<Chapter>) {
        for (i in 1 until chapters.size) {
            assertTrue(
                "starts must increase: ${chapters.map { it.start }}",
                chapters[i].start > chapters[i - 1].start,
            )
        }
    }
}
