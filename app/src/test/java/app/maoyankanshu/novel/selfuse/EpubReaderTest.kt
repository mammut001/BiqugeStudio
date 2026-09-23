package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * JVM unit tests for [EpubReader]: encoding (BOM / UTF-16 / XML), spine order, HTML/NBSP.
 * minSdk 23 safe — pure Java helpers, no Android APIs.
 */
class EpubReaderTest {

    @Test
    fun decodeText_utf8Bom() {
        val body = "UTF-8 BOM 正文"
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            body.toByteArray(StandardCharsets.UTF_8)
        assertEquals(body, EpubReader.decodeText(bytes))
    }

    @Test
    fun decodeText_utf16LeBom() {
        val body = "UTF-16LE 章节"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
        val payload = body.toByteArray(Charset.forName("UTF-16LE"))
        assertEquals(body, EpubReader.decodeText(bom + payload))
    }

    @Test
    fun decodeText_utf16BeBom() {
        val body = "UTF-16BE 章节"
        val bom = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
        val payload = body.toByteArray(Charset.forName("UTF-16BE"))
        assertEquals(body, EpubReader.decodeText(bom + payload))
    }

    @Test
    fun decodeText_utf32LeBom() {
        val body = "UTF-32LE 章节正文"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        val payload = body.toByteArray(Charset.forName("UTF-32LE"))
        assertEquals(body, EpubReader.decodeText(bom + payload))
    }

    @Test
    fun decodeText_utf32BeBom() {
        val body = "UTF-32BE 章节正文"
        val bom = byteArrayOf(0x00, 0x00, 0xFE.toByte(), 0xFF.toByte())
        val payload = body.toByteArray(Charset.forName("UTF-32BE"))
        assertEquals(body, EpubReader.decodeText(bom + payload))
    }

    @Test
    fun decodeText_utf32LeBomOnly_empty() {
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        assertEquals("", EpubReader.decodeText(bom))
    }

    @Test
    fun decodeText_utf32LeNotMisreadAsUtf16() {
        val body = "无NUL"
        val bom = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00)
        val payload = body.toByteArray(Charset.forName("UTF-32LE"))
        val decoded = EpubReader.decodeText(bom + payload)
        assertEquals(body, decoded)
        assertFalse(decoded.contains('\u0000'))
    }

    @Test
    fun decodeText_nullAndEmpty_returnEmpty() {
        assertEquals("", EpubReader.decodeText(null))
        assertEquals("", EpubReader.decodeText(ByteArray(0)))
    }

    @Test
    fun decodeText_utf16LeXmlSignatureWithoutBom() {
        val xml = "<?xml version=\"1.0\"?><p>十六位</p>"
        val bytes = xml.toByteArray(Charset.forName("UTF-16LE"))
        assertFalse(bytes[0] == 0xFF.toByte())
        assertEquals(xml, EpubReader.decodeText(bytes))
    }

    @Test
    fun decodeText_xmlEncodingDeclarationUtf8() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><html><body>声明编码</body></html>"""
        assertEquals(xml, EpubReader.decodeText(xml.toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun decodeText_plainUtf8Default() {
        assertEquals("你好", EpubReader.decodeText("你好".toByteArray(StandardCharsets.UTF_8)))
    }

    @Test
    fun stripHtml_nbspNamedAndNumericToU0020() {
        assertEquals("a b", EpubReader.stripHtml("<p>a&nbsp;b</p>"))
        assertEquals("a b", EpubReader.stripHtml("<p>a&#160;b</p>"))
        assertEquals("a b", EpubReader.stripHtml("<p>a&#xA0;b</p>"))
        assertFalse(EpubReader.stripHtml("<p>a&#160;b</p>").contains('\u00A0'))
    }

    @Test
    fun stripHtml_entitiesAndBlockTags() {
        val html = "<p>A&amp;B&quot;C&quot;</p><br/><div>Line&lt;two&gt;</div>"
        assertEquals("A&B\"C\"\nLine<two>", EpubReader.stripHtml(html))
    }

    @Test
    fun decodeHtmlEntities_quotAmp() {
        assertEquals(
            "A & B \"ok\"",
            EpubReader.decodeHtmlEntities("A &amp; B &quot;ok&quot;"),
        )
    }

    @Test
    fun spineFiles_xhtmlHtmlInPackageOrder() {
        val opf = """
            <package>
              <manifest>
                <item id="c2" href="b.xhtml" media-type="application/xhtml+xml"/>
                <item id="c1" href="a.html" media-type="text/html"/>
                <item id="css" href="style.css" media-type="text/css"/>
              </manifest>
              <spine>
                <itemref idref="c1"/>
                <itemref idref="c2"/>
              </spine>
            </package>
        """.trimIndent()
        val spine = EpubReader.spineFiles(opf, "OEBPS/content.opf")
        assertEquals(listOf("OEBPS/a.html", "OEBPS/b.xhtml"), spine)
    }

    @Test
    fun spineFiles_skipsNonHtml() {
        val opf = """
            <package>
              <manifest>
                <item id="n" href="nav.ncx" media-type="application/x-dtbncx+xml"/>
                <item id="c" href="ch.xhtml" media-type="application/xhtml+xml"/>
              </manifest>
              <spine>
                <itemref idref="n"/>
                <itemref idref="c"/>
              </spine>
            </package>
        """.trimIndent()
        assertEquals(listOf("OEBPS/ch.xhtml"), EpubReader.spineFiles(opf, "OEBPS/content.opf"))
    }

    @Test
    fun read_followsSpineNotZipOrder() {
        // ZIP stores second.html first; spine is first → second.
        // stripHtml turns </p> into \n then trims; join must be single \n, not \n\n.
        val zip = buildEpub(
            chaptersInZipOrder = listOf(
                "OEBPS/second.html" to "<p>第二</p>",
                "OEBPS/first.html" to "<p>第一</p>",
            ),
            spineIdRefs = listOf("first", "second"),
            manifest = listOf(
                "first" to "first.html",
                "second" to "second.html",
            ),
            chapterCharset = StandardCharsets.UTF_8,
            withBom = false,
        )
        val text = EpubReader.read(ByteArrayInputStream(zip))
        assertTrue("spine order", text.indexOf("第一") < text.indexOf("第二"))
        assertFalse("no blank line between chapters", text.contains("\n\n"))
        assertEquals("第一\n第二", text)
    }

    @Test
    fun read_utf16LeChapterWithBom() {
        val body = "UTF-16 书页"
        val zip = buildEpub(
            chaptersInZipOrder = listOf("OEBPS/chap1.html" to body),
            spineIdRefs = listOf("c1"),
            manifest = listOf("c1" to "chap1.html"),
            chapterCharset = Charset.forName("UTF-16LE"),
            withBom = true,
        )
        val text = EpubReader.read(ByteArrayInputStream(zip))
        assertTrue(text.contains(body))
    }

    @Test
    fun read_htmlEntitiesInChapter() {
        val zip = buildEpub(
            chaptersInZipOrder = listOf(
                "OEBPS/chap1.html" to "Hello&nbsp;&amp;&nbsp;&quot;EPUB&quot;",
            ),
            spineIdRefs = listOf("c1"),
            manifest = listOf("c1" to "chap1.html"),
            chapterCharset = StandardCharsets.UTF_8,
            withBom = false,
        )
        val text = EpubReader.read(ByteArrayInputStream(zip))
        assertEquals("Hello & \"EPUB\"", text)
    }

    @Test
    fun parsePackageMetadata_dcPrefixAndEntities() {
        val opf = """
            <package>
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>A&amp;B &quot;Title&quot;</dc:title>
                <dc:creator>Jane&nbsp;Doe</dc:creator>
              </metadata>
            </package>
        """.trimIndent()
        val meta = EpubReader.parsePackageMetadata(opf)
        assertEquals("A&B \"Title\"", meta[0])
        assertEquals("Jane Doe", meta[1])
    }

    @Test
    fun parsePackageMetadata_unprefixedAndMissing() {
        val withTitleOnly = """
            <metadata>
              <title xmlns="http://purl.org/dc/elements/1.1/">Only Title</title>
            </metadata>
        """.trimIndent()
        val meta = EpubReader.parsePackageMetadata(withTitleOnly)
        assertEquals("Only Title", meta[0])
        assertEquals(null, meta[1])

        val empty = EpubReader.parsePackageMetadata("<package/>")
        assertEquals(null, empty[0])
        assertEquals(null, empty[1])
    }

    @Test
    fun parsePackageMetadata_skipsBlankTitle() {
        val opf = """
            <metadata>
              <dc:title>   </dc:title>
              <dc:title>Second</dc:title>
              <dc:creator>Auth</dc:creator>
            </metadata>
        """.trimIndent()
        val meta = EpubReader.parsePackageMetadata(opf)
        assertEquals("Second", meta[0])
        assertEquals("Auth", meta[1])
    }

    @Test
    fun readBook_returnsMetadataAndText() {
        val zip = buildEpub(
            chaptersInZipOrder = listOf("OEBPS/chap1.html" to "<p>Chapter body</p>"),
            spineIdRefs = listOf("c1"),
            manifest = listOf("c1" to "chap1.html"),
            chapterCharset = StandardCharsets.UTF_8,
            withBom = false,
            dcTitle = "Embedded Title",
            dcCreator = "Embedded Author",
        )
        val book = EpubReader.readBook(ByteArrayInputStream(zip))
        assertEquals("Embedded Title", book.title)
        assertEquals("Embedded Author", book.author)
        assertTrue(book.text.contains("Chapter body"))
        // read() remains text-only compatible
        assertEquals(book.text, EpubReader.read(ByteArrayInputStream(zip)))
    }

    @Test
    fun looksLikeImage_jpegPngGifWebp() {
        assertTrue(EpubReader.looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x00)))
        assertTrue(EpubReader.looksLikeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)))
        assertTrue(EpubReader.looksLikeImage("GIF89a".toByteArray(StandardCharsets.US_ASCII)))
        val webp = ByteArray(12)
        "RIFF".toByteArray().copyInto(webp, 0)
        "WEBP".toByteArray().copyInto(webp, 8)
        assertTrue(EpubReader.looksLikeImage(webp))
        assertFalse(EpubReader.looksLikeImage("not-an-image".toByteArray()))
        assertFalse(EpubReader.looksLikeImage(byteArrayOf(1, 2)))
    }

    @Test
    fun resolveCoverHref_epub2MetaAndEpub3Properties() {
        val opf2 = """
            <package>
              <metadata><meta name="cover" content="cov"/></metadata>
              <manifest>
                <item id="cov" href="images/c.jpg" media-type="image/jpeg"/>
                <item id="c1" href="ch.html" media-type="application/xhtml+xml"/>
              </manifest>
            </package>
        """.trimIndent()
        assertEquals("OEBPS/images/c.jpg", EpubReader.resolveCoverHref(opf2, "OEBPS/content.opf"))

        val opf3 = """
            <package>
              <manifest>
                <item id="cov" href="cover.png" media-type="image/png" properties="cover-image"/>
              </manifest>
            </package>
        """.trimIndent()
        assertEquals("OEBPS/cover.png", EpubReader.resolveCoverHref(opf3, "OEBPS/content.opf"))
        assertEquals(null, EpubReader.resolveCoverHref("<package/>", "OEBPS/content.opf"))
    }

    @Test
    fun extractCoverBytes_rejectsOversizedAndMissing() {
        val png = minimalPngBytes()
        val files = mapOf("OEBPS/cover.png" to png)
        val opf = """
            <package>
              <manifest>
                <item id="cov" href="cover.png" media-type="image/png" properties="cover-image"/>
              </manifest>
            </package>
        """.trimIndent()
        val ok = EpubReader.extractCoverBytes(files, opf, "OEBPS/content.opf")
        assertTrue(ok != null && ok.contentEquals(png))

        val huge = ByteArray(EpubReader.MAX_COVER_BYTES + 1) { 0xFF.toByte() }
        huge[0] = 0xFF.toByte()
        huge[1] = 0xD8.toByte()
        val filesHuge = mapOf("OEBPS/cover.png" to huge)
        assertEquals(null, EpubReader.extractCoverBytes(filesHuge, opf, "OEBPS/content.opf"))

        assertEquals(null, EpubReader.extractCoverBytes(emptyMap(), opf, "OEBPS/content.opf"))
    }

    @Test
    fun maxBytes_matchesLocalBookImport32MiB() {
        assertEquals(32 * 1024 * 1024, EpubReader.MAX_BYTES)
    }

    private fun minimalPngBytes(): ByteArray =
        java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==",
        )

    @Test
    fun readAllBounded_rejectsWhenEntryExceeds32MiB() {
        val payload = ByteArray(EpubReader.MAX_BYTES + 1) { 1 }
        val total = longArrayOf(0L)
        val ex = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EpubReader.readAllBounded(ByteArrayInputStream(payload), total)
        }
        assertTrue(ex.message?.contains("file too large") == true)
        assertTrue(ex.message?.contains("32MB") == true)
    }

    @Test
    fun readAllBounded_rejectsWhenTotalExceeds32MiB() {
        val chunk = ByteArray(1024) { 2 }
        val total = longArrayOf(EpubReader.MAX_BYTES - 512L)
        val ex = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EpubReader.readAllBounded(ByteArrayInputStream(chunk), total)
        }
        assertTrue(ex.message?.contains("file too large") == true)
    }

    @Test
    fun readAllBounded_allowsWithinLimit() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val total = longArrayOf(0L)
        val out = EpubReader.readAllBounded(ByteArrayInputStream(payload), total)
        assertEquals(5, out.size)
        assertEquals(5L, total[0])
    }

    @Test
    fun read_oversizedStoredEntryThrowsIllegalArgumentException() {
        // STORED entry larger than 32 MiB: fail-fast via ZipEntry.getSize() or stream cap.
        val oversized = ByteArray(EpubReader.MAX_BYTES + 1024)
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <manifest>
    <item id="c1" href="chap1.html" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="c1"/>
  </spine>
</package>""".toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

            val entry = ZipEntry("OEBPS/chap1.html")
            entry.method = ZipEntry.STORED
            entry.size = oversized.size.toLong()
            val crc = java.util.zip.CRC32()
            crc.update(oversized)
            entry.crc = crc.value
            zip.putNextEntry(entry)
            zip.write(oversized)
            zip.closeEntry()
        }

        val ex = org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            EpubReader.read(ByteArrayInputStream(baos.toByteArray()))
        }
        assertTrue(ex.message?.contains("file too large") == true)
        assertTrue(ex.message?.contains("32MB") == true)
    }

    @Test
    fun stripHtml_headTagStripped() {
        val html = "<html><head><title>Meta Header</title><style>p { color: red; }</style></head><body><h1>Heading</h1><p>Body</p></body></html>"
        val text = EpubReader.stripHtml(html)
        assertFalse("Metadata in <head> must be stripped", text.contains("Meta Header"))
        assertEquals("Heading\nBody", text)
    }

    @Test
    fun stripHtml_headingTagsPutOnOwnLine() {
        val html = "<div><h1>Chapter 1</h1><p>Text</p></div>"
        assertEquals("Chapter 1\nText", EpubReader.stripHtml(html))
    }

    @Test
    fun parseNcxEntries_extractsOrderedEntries() {
        val ncx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="p1" playOrder="1">
                  <navLabel><text>第一章 序章</text></navLabel>
                  <content src="Text/chap1.xhtml"/>
                </navPoint>
                <navPoint id="p2" playOrder="2">
                  <navLabel><text>第二章 启程</text></navLabel>
                  <content src="Text/chap2.xhtml#part1"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()
        val entries = EpubReader.parseNcxEntries(ncx, "OEBPS/toc.ncx")
        assertEquals(2, entries.size)
        assertEquals("第一章 序章", entries[0].title)
        assertEquals("OEBPS/Text/chap1.xhtml", entries[0].file)
        assertEquals(null, entries[0].anchor)
        assertEquals("第二章 启程", entries[1].title)
        assertEquals("OEBPS/Text/chap2.xhtml", entries[1].file)
        assertEquals("part1", entries[1].anchor)
    }

    @Test
    fun parseNavEntries_extractsOrderedEntries() {
        val nav = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="toc" id="toc">
                  <h1>Table of Contents</h1>
                  <ol>
                    <li><a href="chap1.xhtml">Chapter 1 &amp; Intro</a></li>
                    <li><a href="chap2.xhtml#sub">Chapter 2</a></li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
        val entries = EpubReader.parseNavEntries(nav, "OEBPS/nav.xhtml")
        assertEquals(2, entries.size)
        assertEquals("Chapter 1 & Intro", entries[0].title)
        assertEquals("OEBPS/chap1.xhtml", entries[0].file)
        assertEquals("Chapter 2", entries[1].title)
        assertEquals("sub", entries[1].anchor)
    }

    @Test
    fun alreadyStartsWithTitle_matchesNormalized() {
        assertTrue(EpubReader.alreadyStartsWithTitle("第一章 序章\n正文", "第一章 序章"))
        assertTrue(EpubReader.alreadyStartsWithTitle("Chapter 1: The Boy Who Lived\nBody", "Chapter 1"))
        assertFalse(EpubReader.alreadyStartsWithTitle("正文开始\n没有标题", "第一章 序章"))
    }

    @Test
    fun formatChapterHeading_wrapsUnrecognized() {
        assertEquals("第一章 序章", EpubReader.formatChapterHeading("第一章 序章"))
        assertEquals("Chapter 1", EpubReader.formatChapterHeading("Chapter 1"))
        assertEquals("引子", EpubReader.formatChapterHeading("引子"))
        assertEquals("1. 缘起", EpubReader.formatChapterHeading("1. 缘起"))
        assertEquals("一、初入江湖", EpubReader.formatChapterHeading("一、初入江湖"))
        assertEquals("【A Long-expected Party】", EpubReader.formatChapterHeading("A Long-expected Party"))
    }

    @Test
    fun readBook_withEpub2Ncx_populatesTocAndInjectsHeadings() {
        val ncx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <navMap>
                <navPoint id="p1" playOrder="1">
                  <navLabel><text>第一章 序章</text></navLabel>
                  <content src="chap1.html"/>
                </navPoint>
                <navPoint id="p2" playOrder="2">
                  <navLabel><text>第二章 启程</text></navLabel>
                  <content src="chap2.html"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()
        val zip = buildEpub(
            chaptersInZipOrder = listOf(
                "OEBPS/chap1.html" to "<p>正文一内容</p>",
                "OEBPS/chap2.html" to "<p>正文二内容</p>",
            ),
            spineIdRefs = listOf("c1", "c2"),
            manifest = listOf(
                "c1" to "chap1.html",
                "c2" to "chap2.html",
            ),
            chapterCharset = StandardCharsets.UTF_8,
            withBom = false,
            ncxPath = "OEBPS/toc.ncx",
            ncxContent = ncx,
        )
        val book = EpubReader.readBook(ByteArrayInputStream(zip))
        assertEquals(2, book.toc.size)
        assertEquals("第一章 序章", book.toc[0].title)
        assertEquals("第二章 启程", book.toc[1].title)
        assertTrue("Chapter 1 title must be injected into text", book.text.contains("第一章 序章"))
        assertTrue("Chapter 2 title must be injected into text", book.text.contains("第二章 启程"))
    }

    @Test
    fun readBook_withEpub3Nav_populatesTocAndAvoidsDuplicatingExistingHeading() {
        val nav = """
            <?xml version="1.0" encoding="utf-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="chap1.html">第一章 序章</a></li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()
        val zip = buildEpub(
            chaptersInZipOrder = listOf(
                "OEBPS/chap1.html" to "<h1>第一章 序章</h1><p>正文一内容</p>",
            ),
            spineIdRefs = listOf("c1"),
            manifest = listOf(
                "c1" to "chap1.html",
            ),
            chapterCharset = StandardCharsets.UTF_8,
            withBom = false,
            navPath = "OEBPS/nav.xhtml",
            navContent = nav,
        )
        val book = EpubReader.readBook(ByteArrayInputStream(zip))
        assertEquals(1, book.toc.size)
        assertEquals("第一章 序章", book.toc[0].title)
        val occurrences = book.text.split("第一章 序章").size - 1
        assertEquals("Existing <h1> title must not be duplicated", 1, occurrences)
    }

    private fun buildEpub(
        chaptersInZipOrder: List<Pair<String, String>>,
        spineIdRefs: List<String>,
        manifest: List<Pair<String, String>>,
        chapterCharset: Charset,
        withBom: Boolean,
        dcTitle: String? = null,
        dcCreator: String? = null,
        ncxPath: String? = null,
        ncxContent: String? = null,
        navPath: String? = null,
        navContent: String? = null,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

            val allManifest = manifest.toMutableList()
            if (ncxPath != null && ncxContent != null && allManifest.none { it.first == "ncx" }) {
                allManifest.add("ncx" to ncxPath.removePrefix("OEBPS/"))
            }
            if (navPath != null && navContent != null && allManifest.none { it.first == "nav" }) {
                allManifest.add("nav" to navPath.removePrefix("OEBPS/"))
            }

            val manifestXml = allManifest.joinToString("\n") { (id, href) ->
                val mediaType = when {
                    href.endsWith(".ncx") -> "application/x-dtbncx+xml"
                    else -> "application/xhtml+xml"
                }
                val props = if (id == "nav") """ properties="nav"""" else ""
                """    <item id="$id" href="$href" media-type="$mediaType"$props/>"""
            }
            val spineXml = spineIdRefs.joinToString("\n") { id ->
                """    <itemref idref="$id"/>"""
            }
            val spineTag = if (ncxContent != null) """  <spine toc="ncx">""" else """  <spine>"""
            val metadata = buildString {
                if (dcTitle != null || dcCreator != null) {
                    append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                    if (dcTitle != null) append("    <dc:title>$dcTitle</dc:title>\n")
                    if (dcCreator != null) append("    <dc:creator>$dcCreator</dc:creator>\n")
                    append("  </metadata>\n")
                }
            }
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
$metadata  <manifest>
$manifestXml
  </manifest>
$spineTag
$spineXml
  </spine>
</package>""".toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

            if (ncxPath != null && ncxContent != null) {
                zip.putNextEntry(ZipEntry(ncxPath))
                zip.write(ncxContent.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
            if (navPath != null && navContent != null) {
                zip.putNextEntry(ZipEntry(navPath))
                zip.write(navContent.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }

            for ((path, content) in chaptersInZipOrder) {
                zip.putNextEntry(ZipEntry(path))
                val html = if (content.trimStart().startsWith("<")) {
                    content
                } else {
                    "<html><body><p>$content</p></body></html>"
                }
                val payload = html.toByteArray(chapterCharset)
                when {
                    withBom && chapterCharset.name().equals("UTF-16LE", ignoreCase = true) -> {
                        zip.write(byteArrayOf(0xFF.toByte(), 0xFE.toByte()))
                        zip.write(payload)
                    }
                    withBom && chapterCharset.name().equals("UTF-16BE", ignoreCase = true) -> {
                        zip.write(byteArrayOf(0xFE.toByte(), 0xFF.toByte()))
                        zip.write(payload)
                    }
                    withBom && chapterCharset == StandardCharsets.UTF_8 -> {
                        zip.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                        zip.write(payload)
                    }
                    else -> zip.write(payload)
                }
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }
}
