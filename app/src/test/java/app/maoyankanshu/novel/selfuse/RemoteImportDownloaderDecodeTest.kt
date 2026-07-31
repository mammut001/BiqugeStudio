package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * JVM tests for [RemoteImportDownloader.decodeText] (no network).
 * Mirrors plain TXT BOM paths used after HTTPS body download.
 */
class RemoteImportDownloaderDecodeTest {

    @Test
    fun decodeText_utf8AndBom() {
        val body = "远程 UTF-8"
        assertEquals(body, RemoteImportDownloader.decodeText(body.toByteArray(StandardCharsets.UTF_8)))
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        assertEquals(
            body,
            RemoteImportDownloader.decodeText(bom + body.toByteArray(StandardCharsets.UTF_8)),
        )
    }

    @Test
    fun decodeText_utf16LeBe() {
        val body = "远程十六位"
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            body.toByteArray(Charset.forName("UTF-16LE"))
        val be = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) +
            body.toByteArray(Charset.forName("UTF-16BE"))
        assertEquals(body, RemoteImportDownloader.decodeText(le))
        assertEquals(body, RemoteImportDownloader.decodeText(be))
    }

    @Test
    fun decodeText_utf32LeBe() {
        val body = "远程 UTF-32 文本"
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00) +
            body.toByteArray(Charset.forName("UTF-32LE"))
        val be = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte()) +
            body.toByteArray(Charset.forName("UTF-32BE"))
        assertEquals(body, RemoteImportDownloader.decodeText(le))
        assertEquals(body, RemoteImportDownloader.decodeText(be))
        assertFalse(RemoteImportDownloader.decodeText(le).contains('\u0000'))
    }

    @Test
    fun decodeText_utf32BomOnly_empty() {
        assertEquals(
            "",
            RemoteImportDownloader.decodeText(
                byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00),
            ),
        )
    }

    @Test
    fun decodeText_gb18030() {
        val body = "远程 GB18030 中文"
        assertEquals(
            body,
            RemoteImportDownloader.decodeText(body.toByteArray(Charset.forName("GB18030"))),
        )
    }

    @Test
    fun decodeText_emptyAndShort_noThrow() {
        assertEquals("", RemoteImportDownloader.decodeText(ByteArray(0)))
        assertEquals(
            "",
            RemoteImportDownloader.decodeText(byteArrayOf(0xFF.toByte(), 0xFE.toByte())),
        )
        RemoteImportDownloader.decodeText(byteArrayOf(0xFF.toByte()))
    }
}
