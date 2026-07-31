package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * JVM tests for [PlainTextDecoder] (shared by local/remote TXT import).
 * Covers UTF-32/16/8 BOM, GB18030 fallback, empty/malformed edge cases.
 */
class PlainTextDecoderTest {

    @Test
    fun empty_returnsEmpty() {
        assertEquals("", PlainTextDecoder.decode(ByteArray(0)))
    }

    @Test
    fun utf8_plainAndBom() {
        val body = "UTF-8 正文"
        assertEquals(body, PlainTextDecoder.decode(body.toByteArray(StandardCharsets.UTF_8)))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(
            body,
            PlainTextDecoder.decode(bom + body.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    @Test
    fun utf16LeAndBe_withBom() {
        val body = "十六位"
        val leBom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val beBom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        assertEquals(
            body,
            PlainTextDecoder.decode(leBom + body.toByteArray(Charset.forName("UTF-16LE"))),
        )
        assertEquals(
            body,
            PlainTextDecoder.decode(beBom + body.toByteArray(Charset.forName("UTF-16BE"))),
        )
    }

    @Test
    fun utf32Le_withBom_stripsBom() {
        val body = "UTF-32LE 纯文本"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        val payload = body.toByteArray(Charset.forName("UTF-32LE"))
        assertTrue(PlainTextDecoder.isUtf32LeBom(bom + payload))
        assertEquals(body, PlainTextDecoder.decode(bom + payload))
    }

    @Test
    fun utf32Be_withBom_stripsBom() {
        val body = "UTF-32BE 纯文本"
        val bom = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        val payload = body.toByteArray(Charset.forName("UTF-32BE"))
        assertTrue(PlainTextDecoder.isUtf32BeBom(bom + payload))
        assertEquals(body, PlainTextDecoder.decode(bom + payload))
    }

    @Test
    fun utf32Le_bomOnly_emptyPayload() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        assertEquals("", PlainTextDecoder.decode(bom))
    }

    @Test
    fun utf32Be_bomOnly_emptyPayload() {
        val bom = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        assertEquals("", PlainTextDecoder.decode(bom))
    }

    @Test
    fun utf32Le_notConfusedWithUtf16Le() {
        val body = "区分UTF32"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        val decoded = PlainTextDecoder.decode(bom + body.toByteArray(Charset.forName("UTF-32LE")))
        assertEquals(body, decoded)
        assertFalse(decoded.contains('\u0000'))
    }

    @Test
    fun utf16Le_stillWorksWhenNotUtf32() {
        // FF FE without trailing 00 00 is UTF-16LE (e.g. first code unit is non-zero).
        val body = "仍是UTF16"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val payload = body.toByteArray(Charset.forName("UTF-16LE"))
        // Ensure payload does not accidentally form UTF-32 BOM (already only 2-byte BOM).
        assertFalse(PlainTextDecoder.isUtf32LeBom(bom + payload))
        assertEquals(body, PlainTextDecoder.decode(bom + payload))
    }

    @Test
    fun gb18030_whenUtf8WouldReplace() {
        val body = "这是 GB18030 简体中文"
        val bytes = body.toByteArray(Charset.forName("GB18030"))
        assertEquals(body, PlainTextDecoder.decode(bytes))
    }

    @Test
    fun shortGarbage_doesNotThrow() {
        // Truncated / non-text bytes: must return without crashing.
        val junk = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) // bare UTF-16LE BOM, empty body
        assertEquals("", PlainTextDecoder.decode(junk))
        val single = byteArrayOf(0xFF.toByte())
        // Single 0xFF is invalid UTF-8 → U+FFFD path may use GB18030; just ensure no throw.
        PlainTextDecoder.decode(single)
        PlainTextDecoder.decode(byteArrayOf(0x00, 0x00))
    }
}
