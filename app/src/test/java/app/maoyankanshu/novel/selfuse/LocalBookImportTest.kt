package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun isEpub_extensionAndMime() {
        assertTrue(LocalBookImport.isEpub("book.epub", null))
        assertTrue(LocalBookImport.isEpub("Book.EPUB", null))
        assertTrue(LocalBookImport.isEpub(null, "application/epub+zip"))
        assertTrue(LocalBookImport.isEpub("doc", "application/epub+zip"))
        assertTrue(LocalBookImport.isEpub("doc", "application/epub+zip; charset=binary"))
        assertTrue(LocalBookImport.isEpub("x.epub", "text/plain")) // extension wins
        assertFalse(LocalBookImport.isEpub("doc", null))
        assertFalse(LocalBookImport.isEpub("story.txt", "text/plain"))
        assertFalse(LocalBookImport.isEpub("story.txt", "application/octet-stream"))
        assertFalse(LocalBookImport.isEpub(null, null))
        assertFalse(LocalBookImport.isEpub(null, "application/zip"))
    }

    /**
     * Regression: some SAF providers report MIME application/epub+zip but a display
     * name without ".epub". Without MIME-aware detection this was misread as TXT.
     */
    @Test
    fun testEpubImport_mimeOnlyWithoutEpubExtension_regression() {
        val chapter = "MIME-only EPUB body that must not be decoded as plain text ZIP bytes"
        val epubBytes = createMinimalEpubZip(chapter)
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "document", // no .epub suffix (DocumentsUI-style)
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
            mimeType = LocalBookImport.MIME_EPUB,
        )
        assertEquals("document", imported.title)
        assertEquals("EPUB作者", imported.author)
        assertTrue(
            "expected EPUB chapter text, got: ${imported.text.take(80)}",
            imported.text.contains(chapter),
        )
        // Plain-text path would embed PK zip headers or fail — ensure we got real text.
        assertFalse(imported.text.startsWith("PK"))
    }

    @Test
    fun testEpubImport_mimeWithParameters_noExtension() {
        val epubBytes = createMinimalEpubZip("参数 MIME 章节")
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "untitled",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
            mimeType = "application/epub+zip; charset=binary",
        )
        assertEquals("EPUB作者", imported.author)
        assertTrue(imported.text.contains("参数 MIME 章节"))
    }

    @Test
    fun testTxtImport_notEpubWhenMimeIsOctetStream() {
        val text = "plain novel line"
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(text.toByteArray(StandardCharsets.UTF_8)),
            rawName = "mystery",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
            mimeType = "application/octet-stream",
        )
        assertEquals("TXT作者", imported.author)
        assertEquals(text, imported.text)
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
    fun testOversizedTxtFileThrowsException() {
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

        val ex = assertThrows(IllegalArgumentException::class.java) {
            LocalBookImport.fromStream(
                stream = oversizedStream,
                rawName = "oversized_book.txt",
                defaultName = "默认书名",
                authorEpub = "EPUB作者",
                authorTxt = "TXT作者",
            )
        }
        assertTrue(ex.message?.contains("file too large") == true)
    }

    @Test
    fun testOversizedEpubThrowsException() {
        val targetSize = (32 * 1024 * 1024) + 1024
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write("""<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <manifest>
    <item id="chapter1" href="chap1.html" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="chapter1"/>
  </spine>
</package>""".toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()

            val entry = ZipEntry("OEBPS/chap1.html")
            entry.method = ZipEntry.STORED
            val dummyData = ByteArray(targetSize)
            entry.size = dummyData.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(dummyData)
            entry.crc = crc.value
            zip.putNextEntry(entry)
            zip.write(dummyData)
            zip.closeEntry()
        }

        val ex = assertThrows(IllegalArgumentException::class.java) {
            LocalBookImport.fromStream(
                stream = ByteArrayInputStream(baos.toByteArray()),
                rawName = "sample_large.epub",
                defaultName = "默认书名",
                authorEpub = "EPUB作者",
                authorTxt = "TXT作者",
            )
        }
        assertTrue(ex.message?.contains("file too large") == true)
    }
    @Test
    fun testRawNameFileNameParsing() {
        val bytes = "Simple Text".toByteArray(StandardCharsets.UTF_8)
        val importedTxt = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(bytes),
            rawName = "my_custom_book.txt",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("my_custom_book", importedTxt.title)
        assertEquals("TXT作者", importedTxt.author)

        val epubBytes = createMinimalEpubZip("EPUB 内容")
        val importedEpub = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "my_custom_novel.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("my_custom_novel", importedEpub.title)
        assertEquals("EPUB作者", importedEpub.author)
    }

    @Test
    fun testEpubImport_prefersEmbeddedOpfMetadata() {
        val epubBytes = createMinimalEpubZip(
            chapterHtmlContent = "正文一章",
            dcTitle = "三体",
            dcCreator = "刘慈欣",
        )
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "wrong_filename.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("三体", imported.title)
        assertEquals("刘慈欣", imported.author)
        assertTrue(imported.text.contains("正文一章"))
    }

    @Test
    fun testEpubImport_missingMetadata_fallsBackToFilenameAndAuthorEpub() {
        val epubBytes = createMinimalEpubZip("无元数据正文")
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "fallback_book.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("fallback_book", imported.title)
        assertEquals("EPUB作者", imported.author)
        assertTrue(imported.text.contains("无元数据正文"))
    }

    @Test
    fun testEpubImport_blankMetadata_fallsBack() {
        val epubBytes = createMinimalEpubZip(
            chapterHtmlContent = "空白元数据",
            dcTitle = "   ",
            dcCreator = "",
        )
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "blank_meta.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("blank_meta", imported.title)
        assertEquals("EPUB作者", imported.author)
    }

    @Test
    fun testEpubImport_decodesEntitiesInMetadata() {
        val epubBytes = createMinimalEpubZip(
            chapterHtmlContent = "entity body",
            dcTitle = "A&amp;B &quot;Gate&quot;",
            dcCreator = "O&apos;Brien",
            useDcPrefix = true,
        )
        val imported = LocalBookImport.fromStream(
            stream = ByteArrayInputStream(epubBytes),
            rawName = "entities.epub",
            defaultName = "默认书名",
            authorEpub = "EPUB作者",
            authorTxt = "TXT作者",
        )
        assertEquals("A&B \"Gate\"", imported.title)
        assertEquals("O'Brien", imported.author)
    }

    private fun createMinimalEpubZip(
        chapterHtmlContent: String,
        dcTitle: String? = null,
        dcCreator: String? = null,
        useDcPrefix: Boolean = false,
    ): ByteArray {
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

            val titleTag = if (useDcPrefix) "dc:title" else "title"
            val creatorTag = if (useDcPrefix) "dc:creator" else "creator"
            val metadata = buildString {
                if (dcTitle != null || dcCreator != null) {
                    append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                    if (dcTitle != null) {
                        append("    <$titleTag>$dcTitle</$titleTag>\n")
                    }
                    if (dcCreator != null) {
                        append("    <$creatorTag>$dcCreator</$creatorTag>\n")
                    }
                    append("  </metadata>\n")
                }
            }

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
$metadata  <manifest>
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
