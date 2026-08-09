package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun prefersFirstParagraphBreakEvenIfShort() {
        val text = "短段。\n" + "甲".repeat(200)
        val end = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertEquals(4, end)
        assertEquals('\n', text[end - 1])
    }

    @Test
    fun prefersSentenceBreakWithinWindowWhenNoNewline() {
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
    fun hardCut_neverSplitsUnicodeSurrogatePair() {
        val prefix = "字".repeat(TtsSpeechChunks.MAX_CHUNK_CHARS - 1)
        val text = prefix + "😀" + "尾".repeat(20)
        val end = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertEquals(prefix.length, end)
        assertFalse(text[end - 1].isHighSurrogate())
        assertTrue(text[end].isHighSurrogate())
        assertTrue(text[end + 1].isLowSurrogate())
    }

    @Test
    fun hardCut_neverSplitsCrLfPair() {
        val prefix = "字".repeat(TtsSpeechChunks.MAX_CHUNK_CHARS - 1)
        val text = prefix + "\r\n" + "下一段"
        val end = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertEquals(prefix.length, end)
        assertEquals('\r', text[end])
        assertEquals('\n', text[end + 1])
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

    @Test
    fun longParagraphIsSplitAcrossChunks() {
        val text = "字".repeat(TtsSpeechChunks.MAX_CHUNK_CHARS + 120) + "\n下一段"
        val first = TtsSpeechChunks.nextChunkEnd(text, 0)
        assertEquals(TtsSpeechChunks.MAX_CHUNK_CHARS, first)
        val second = TtsSpeechChunks.nextChunkEnd(text, first)
        assertTrue(second > first)
        assertEquals('\n', text[second - 1])
    }

    @Test
    fun paragraphRange_empty() {
        assertEquals(IntRange.EMPTY, TtsSpeechChunks.paragraphRangeContaining("", 0))
    }

    @Test
    fun paragraphRange_firstAndMiddle() {
        val text = "第一段内容。\n第二段内容。\n第三段"
        val first = TtsSpeechChunks.paragraphRangeContaining(text, 0)
        assertEquals(0, first.first)
        assertEquals("第一段内容。", text.substring(first.first, first.last + 1))

        val second = TtsSpeechChunks.paragraphRangeContaining(text, text.indexOf('第', 1))
        assertEquals("第二段内容。", text.substring(second.first, second.last + 1))

        val third = TtsSpeechChunks.paragraphRangeContaining(text, text.length - 1)
        assertEquals("第三段", text.substring(third.first, third.last + 1))
    }

    @Test
    fun paragraphRange_trimsAsciiAndFullWidthIndentWhitespace() {
        val text = "　　第一段内容。   \n  第二段内容。"
        val first = TtsSpeechChunks.paragraphRangeContaining(text, 0)
        assertEquals("第一段内容。", text.substring(first.first, first.last + 1))
        assertTrue(first.first >= 2) // two full-width indent characters stay outside highlight

        val secondStart = text.indexOf("第二段")
        val second = TtsSpeechChunks.paragraphRangeContaining(text, secondStart - 1)
        assertEquals("第二段内容。", text.substring(second.first, second.last + 1))
        assertEquals(secondStart, second.first)
    }

    @Test
    fun paragraphRange_stripsCarriageReturn() {
        val text = "一行\r\n二行"
        val first = TtsSpeechChunks.paragraphRangeContaining(text, 0)
        assertEquals("一行", text.substring(first.first, first.last + 1))
    }

    @Test
    fun paragraphRange_offsetOnNewlineUsesFollowingParagraph() {
        val text = "A段\nB段"
        val newlineIndex = text.indexOf('\n')
        val range = TtsSpeechChunks.paragraphRangeContaining(text, newlineIndex)
        assertEquals("B段", text.substring(range.first, range.last + 1))
    }

    @Test
    fun paragraphRange_blankLineBetween() {
        val text = "上\n\n下"
        val middle = TtsSpeechChunks.paragraphRangeContaining(text, 2) // second '\n' area → "下"
        assertEquals("下", text.substring(middle.first, middle.last + 1))
    }

    @Test
    fun paragraphSpeechStart_skipsLeadingWhitespaceAndRejectsBlankParagraph() {
        val text = "第一段\n   第二段内容\n   \n第三段"
        val secondTap = text.indexOf("第二段") + 2
        assertEquals(text.indexOf("第二段"), TtsSpeechChunks.paragraphSpeechStart(text, secondTap))

        val blankLineStart = text.indexOf("   \n第三段")
        assertNull(TtsSpeechChunks.paragraphSpeechStart(text, blankLineStart + 1))
    }

    @Test
    fun trimmedChunkRange_skipsWhitespace() {
        val text = "  \n你好。\n  "
        // indices: 0-1 spaces, 2 \n, 3-5 你好。, 6 \n, 7-8 spaces
        val range = TtsSpeechChunks.trimmedChunkRange(text, 0, 6)
        assertEquals("你好。", text.substring(range.first, range.last + 1))
        assertTrue(TtsSpeechChunks.trimmedChunkRange(text, 0, 3).isEmpty())
    }

    @Test
    fun playbackWatchdog_prefersActualDurationAndKeepsGenerousFallback() {
        assertEquals(35_000L, TtsPlaybackWatchdog.timeoutMs(30_000L, 100))
        assertEquals(90_000L, TtsPlaybackWatchdog.timeoutMs(null, 100))
        assertTrue(TtsPlaybackWatchdog.timeoutMs(70_000L, 10) > 70_000L)
    }

    @Test
    fun followHighlight_overlapCases() {
        val highlight = 10 until 30 // book [10, 30)
        assertEquals(0 until 10, TtsFollowHighlight.overlapInPage(10, 10, highlight)) // page [10,20)
        assertEquals(0 until 5, TtsFollowHighlight.overlapInPage(25, 20, highlight)) // page [25,45) → [25,30)
        assertEquals(5 until 20, TtsFollowHighlight.overlapInPage(5, 20, highlight)) // page [5,25) → [10,25)
        assertNull(TtsFollowHighlight.overlapInPage(40, 10, highlight))
        assertNull(TtsFollowHighlight.overlapInPage(0, 10, null))
    }

    @Test
    fun utteranceIds_parseGeneration() {
        assertEquals(7, TtsUtteranceIds.parseGeneration(TtsUtteranceIds.synth(7, 120)))
        assertEquals(3, TtsUtteranceIds.parseGeneration(TtsUtteranceIds.speak(3, 40)))
        assertNull(TtsUtteranceIds.parseGeneration("broken"))
        assertTrue(TtsUtteranceIds.isSynth("synth-1-2"))
        assertTrue(TtsUtteranceIds.isSpeak("speak-1-2"))
    }
}
