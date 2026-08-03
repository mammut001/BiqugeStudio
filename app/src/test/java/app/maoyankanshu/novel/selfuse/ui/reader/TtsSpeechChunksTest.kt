package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for shipped [TtsSpeechChunks] (TTS ANR-safe chunk sizing). */
class TtsSpeechChunksTest {

    @Test
    fun emptyAndAtEnd() {
        assertEquals(0, TtsSpeechChunks.nextChunkEnd("", 0))
        assertEquals(5, TtsSpeechChunks.nextChunkEnd("hello", 5))
        assertEquals(5, TtsSpeechChunks.nextChunkEnd("hello", 99))
    }

    @Test
    fun prefersSentenceBreakWithinWindow() {
        val prefix = "甲".repeat(120)
        val text = prefix + "这是一句完整的话。后面还有内容" + "乙".repeat(400)
        val end = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertTrue(end > 120)
        assertTrue(end <= TtsSpeechChunks.MAX_CHUNK_CHARS)
        assertEquals('。', text[end - 1])
    }

    @Test
    fun respectsMaxChunk() {
        val text = "字".repeat(2000)
        val end = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertEquals(TtsSpeechChunks.MAX_CHUNK_CHARS, end)
    }

    @Test
    fun shortTailReturnsLength() {
        val text = "很短的一段。"
        assertEquals(text.length, TtsSpeechChunks.nextChunkEnd(text, 0))
    }

    @Test
    fun successiveChunksCoverText() {
        val text = ("第一章 开端。\n" + "正文内容若干。".repeat(80)).repeat(3)
        var offset = 0
        var steps = 0
        while (offset < text.length && steps < 200) {
            val end = TtsSpeechChunks.nextChunkEnd(text, offset)
            assertTrue("end must advance", end > offset)
            offset = end
            steps++
        }
        assertEquals(text.length, offset)
    }
}
