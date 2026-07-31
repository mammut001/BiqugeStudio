package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for [WebImportFetcher] HTML → plain text / entity decoding.
 * No network; pure string helpers (minSdk 23 safe, no android.text.Html).
 */
class WebImportHtmlTest {

    @Test
    fun decodeHtmlEntities_namedAmpNbspQuotLtGt() {
        assertEquals(
            "A & B \"C\" <D> E",
            WebImportFetcher.decodeHtmlEntities("A &amp; B &quot;C&quot; &lt;D&gt; E"),
        )
        assertEquals("a b", WebImportFetcher.decodeHtmlEntities("a&nbsp;b"))
        assertEquals("it's", WebImportFetcher.decodeHtmlEntities("it&apos;s"))
    }

    @Test
    fun decodeHtmlEntities_namedCaseInsensitive() {
        assertEquals("&", WebImportFetcher.decodeHtmlEntities("&AMP;"))
        assertEquals("\"", WebImportFetcher.decodeHtmlEntities("&QUOT;"))
        assertEquals(" ", WebImportFetcher.decodeHtmlEntities("&NbSp;"))
    }

    @Test
    fun decodeHtmlEntities_decimalAndHexNumeric() {
        // &#160; / &#xA0; → U+0020 (not U+00A0 NBSP) for plain-text reading
        assertEquals("a b", WebImportFetcher.decodeHtmlEntities("a&#160;b"))
        assertEquals("a b", WebImportFetcher.decodeHtmlEntities("a&#xA0;b"))
        assertEquals("a b", WebImportFetcher.decodeHtmlEntities("a&#xa0;b"))
        assertFalse(WebImportFetcher.decodeHtmlEntities("a&#160;b").contains('\u00A0'))
        // &#34; = ", &#39; = '
        assertEquals("\"hi\"", WebImportFetcher.decodeHtmlEntities("&#34;hi&#34;"))
        assertEquals("o'clock", WebImportFetcher.decodeHtmlEntities("o&#39;clock"))
    }

    @Test
    fun decodeHtmlEntities_commonTypography() {
        assertEquals("—", WebImportFetcher.decodeHtmlEntities("&mdash;"))
        assertEquals("–", WebImportFetcher.decodeHtmlEntities("&ndash;"))
        assertEquals("…", WebImportFetcher.decodeHtmlEntities("&hellip;"))
        assertEquals("“quote”", WebImportFetcher.decodeHtmlEntities("&ldquo;quote&rdquo;"))
    }

    @Test
    fun decodeHtmlEntities_leavesUnknownIntact() {
        assertEquals("&notarealentity;", WebImportFetcher.decodeHtmlEntities("&notarealentity;"))
        assertEquals("plain", WebImportFetcher.decodeHtmlEntities("plain"))
    }

    @Test
    fun decodeHtmlEntities_ampBeforeOtherEntitiesInSource() {
        // Single-pass: &amp; becomes &; does not re-scan into another entity.
        assertEquals("&lt;", WebImportFetcher.decodeHtmlEntities("&amp;lt;"))
        assertEquals("A & B", WebImportFetcher.decodeHtmlEntities("A &amp; B"))
    }

    @Test
    fun htmlToPlainText_stripsTagsAndDecodesEntities() {
        val html = """
            <html><head><style>p{color:red}</style><title>T</title></head>
            <body>
            <script>evil()</script>
            <p>Hello&nbsp;&amp;&nbsp;world&quot;s&quot;</p>
            <div>Line&lt;two&gt;</div>
            </body></html>
        """.trimIndent()
        val text = WebImportFetcher.htmlToPlainText(html)
        assertTrue(text.contains("Hello & world\"s\""))
        assertTrue(text.contains("Line<two>"))
        assertFalse(text.contains("<p>"))
        assertFalse(text.contains("evil"))
        assertFalse(text.contains("color:red"))
    }

    @Test
    fun htmlToPlainText_blockTagsBecomeNewlines() {
        // normalizeWhitespace collapses adjacent block newlines (</p><br>) to single \n.
        val text = WebImportFetcher.htmlToPlainText("<p>One</p><p>Two</p><br>Three")
        assertEquals("One\nTwo\nThree", text)
    }

    @Test
    fun htmlToPlainText_chineseArticleSnippet() {
        val html = "<p>标题&mdash;&ldquo;阅笺&rdquo;&nbsp;导入&amp;阅读</p>"
        val text = WebImportFetcher.htmlToPlainText(html)
        assertEquals("标题—“阅笺” 导入&阅读", text)
    }
}
