package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalBookImportTest {

    @Test
    fun testUtf8PlainText() {
        val originalText = "Hello, 这是一本 UTF-8 编码的小说内容。"
        val bytes = originalText.toByteArray(StandardCharsets.UTF_8)
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "test_utf8.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("test_utf8", imported.title)
        assertEquals("TXT作者", imported.author)
        assertEquals(originalText, imported.text)
    }

    @Test
    fun testUtf8WithBom() {
        val originalText = "UTF-8 BOM 测试内容\n第二行内容"
        val textBytes = originalText.toByteArray(StandardCharsets.UTF_8)
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
        val bytes = bom + textBytes
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "bom.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("bom", imported.title)
        assertEquals(originalText, imported.text)
    }

    @Test
    fun testUtf16LeWithBom() {
        val originalText = "UTF-16LE 编码小说文本"
        val textBytes = originalText.toByteArray(Charset.forName("UTF-16LE"))
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val bytes = bom + textBytes
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "utf16le.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("utf16le", imported.title)
        assertEquals(originalText, imported.text)
    }

    @Test
    fun testUtf16BeWithBom() {
        val originalText = "UTF-16BE 编码小说文本"
        val textBytes = originalText.toByteArray(Charset.forName("UTF-16BE"))
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val bytes = bom + textBytes
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "utf16be.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("utf16be", imported.title)
        assertEquals(originalText, imported.text)
    }

    @Test
    fun testGb18030Text() {
        val originalText = "这是 GB18030 简体中文编码测试文本，包含繁體字與生僻字。"
        val bytes = originalText.toByteArray(Charset.forName("GB18030"))
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "gb18030.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("gb18030", imported.title)
        assertEquals(originalText, imported.text)
    }

    @Test
    fun testEpubImport() {
        val epubBytes = createMinimalEpubZip("第一章 概述\n欢迎阅读 EPUB 电子书。")
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "sample.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("sample", imported.title)
        assertEquals("EPUB作者", imported.author)
        assertTrue(imported.text.contains("第一章 概述"))
    }

    @Test
    fun testEmptyStreamThrowsException() {
        val emptyBytes = byteArrayOf()
        assertThrows(IllegalArgumentException::class.java) {
            LocalBookImport.fromStream(
                stream = ByteArrayInputStream(emptyBytes),
                rawName = "empty.txt",
                defaultName = "默认书名",
                authorEpub = "EPUB作者",
                authorTxt = "TXT作者",
            )
        }
    }

    @Test
    fun testLarge32MbFileStream() {
        // Construct a ~32 MiB stream filled with repeated text pattern
        val pattern = "第一千二百三十四章 宏大世界观的展开。\n这是一段较长的小说段落文字描述。\n".toByteArray(StandardCharsets.UTF_8)
        val repeats = (32 * 1024 * 1024) / pattern.size
        val totalSize = pattern.size * repeats

        // Stream generator to simulate a 32MB file input stream without consuming 32MB twice
        val repeatingStream = object : java.io.InputStream() {
            private var bytesRead = 0

            override fun read(): Int {
                if (bytesRead >= totalSize) return -1
                val b = pattern[bytesRead % pattern.size].toInt() and 0xFF
                bytesRead++
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= totalSize) return -1
                val remaining = totalSize - bytesRead
                val toRead = Math.min(len, remaining)
                var written = 0
                while (written < toRead) {
                    val chunk = Math.min(toRead - written, pattern.size - (bytesRead % pattern.size))
                    System.arraycopy(pattern, bytesRead % pattern.size, b, off + written, chunk)
                    bytesRead += chunk
                    written += chunk
                }
                return written
            }
        }

        val imported = LocalBookImport.fromStream(
            stream = repeatingStream,
            rawName = "large_book.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("large_book", imported.title)
        assertTrue(imported.text.length > 10_000_000)
    }

    @Test
    fun testOversizedTxtFileCappedAt32Mb() {
        val totalSize = (32 * 1024 * 1024) + 1024
        val oversizedStream = object : java.io.InputStream() {
            private var bytesRead = 0
            override fun read(): Int {
                if (bytesRead >= totalSize) return -1
                bytesRead++
                return 'a'.code
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= totalSize) return -1
                val toRead = Math.min(len, totalSize - bytesRead)
                java.util.Arrays.fill(b, off, off + toRead, 'a'.code.toByte())
                bytesRead += toRead
                return toRead
            }
        }

        val imported = LocalBookImport.fromStream(
            stream = oversizedStream,
            rawName = "oversized_book.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("oversized_book", imported.title)
        assertEquals(32 * 1024 * 1024, imported.text.length)
    }

    @Test
    fun testOversizedEpubImport() {
        val epubBytes = createMinimalEpubZip("第一章 概述\n欢迎阅读 EPUB 电子书。")
        val targetSize = (32 * 1024 * 1024) + 1024
        val oversizedEpubStream = object : java.io.InputStream() {
            private var bytesRead = 0
            override fun read(): Int {
                if (bytesRead >= targetSize) return -1
                val b = if (bytesRead < epubBytes.size) epubBytes[bytesRead].toInt() and 0xFF else 'x'.code
                bytesRead++
                return b
            }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (bytesRead >= targetSize) return -1
                val toRead = Math.min(len, targetSize - bytesRead)
                var written = 0
                while (written < toRead) {
                    val currPos = bytesRead + written
                    if (currPos < epubBytes.size) {
                        val chunk = Math.min(toRead - written, epubBytes.size - currPos)
                        System.arraycopy(epubBytes, currPos, b, off + written, chunk)
                        written += chunk
                    } else {
                        val chunk = toRead - written
                        java.util.Arrays.fill(b, off + written, off + written + chunk, 'x'.code.toByte())
                        written += chunk
                    }
                }
                bytesRead += written
                return written
            }
        }

        val imported = LocalBookImport.fromStream(
            stream = oversizedEpubStream,
            rawName = "sample_large.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("sample_large", imported.title)
        assertEquals("EPUB作者", imported.author)
        assertTrue(imported.text.contains("第一章 概述"))
    }

    private fun createMinimalEpubZip(chapterHtmlContent: String): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            val container = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(StandardCharsets.UTF_8)
            zip.write(container)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <manifest>
    <item id="chapter1" href="chap1.html" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(StandardCharsets.UTF_8)
            zip.write(opf)
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/chap1.html"))
            val html = "<html><body><p>$chapterHtmlContent</p></body></html>".toByteArray(StandardCharsets.UTF_8)
            zip.write(html)
            zip.closeEntry()
        }
        return baos.toByteArray()
    }
}
