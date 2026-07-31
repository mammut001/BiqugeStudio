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

    private fun buildEpub(
        chaptersInZipOrder: List<Pair<String, String>>,
        spineIdRefs: List<String>,
        manifest: List<Pair<String, String>>,
        chapterCharset: Charset,
        withBom: Boolean,
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

            val manifestXml = manifest.joinToString("\n") { (id, href) ->
                """    <item id="$id" href="$href" media-type="application/xhtml+xml"/>"""
            }
            val spineXml = spineIdRefs.joinToString("\n") { id ->
                """    <itemref idref="$id"/>"""
            }
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0">
  <manifest>
$manifestXml
  </manifest>
  <spine>
$spineXml
  </spine>
</package>""".toByteArray(StandardCharsets.UTF_8),
            )
            zip.closeEntry()

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
