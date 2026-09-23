package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadPolicyTest {
    @Test
    fun addressNormalizationAddsHttpsAndRejectsMalformedOrUnsafeInput() {
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress("example.com/books") == "https://example.com/books")
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress(" HTTPS://Example.com/a ") == "https://Example.com/a")
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress("http://example.com") == null)
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress("javascript:alert(1)") == null)
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress("https://") == null)
        assertTrue(BrowserDownloadPolicy.normalizeHttpsAddress(" ") == null)
    }

    @Test
    fun malformedHostsAndPortsAreRejectedBeforeNavigation() {
        for (address in listOf(
            "not a url at all",
            "https://exa mple.com/book.txt",
            "https://example.com:65536/book.epub",
            "https://example.com:0/book.txt",
        )) {
            assertTrue(address, BrowserDownloadPolicy.normalizeHttpsAddress(address) == null)
            assertFalse(address, BrowserDownloadPolicy.isDirectBookUrl(address))
        }
        for (address in listOf(
            "https://例子.中国/books",
            "https://example.com:8443/book%20name.epub",
            "https://[::1]:8443/book.txt",
        )) {
            assertTrue(address, BrowserDownloadPolicy.normalizeHttpsAddress(address) == address)
        }
    }

    @Test
    fun directHttpsTxtAndEpubLinksAreIntercepted() {
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.txt"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/BOOK.EPUB?token=1#x"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?filename=book.txt&token=1"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?download=%E4%B8%89%E5%9B%BD.EPUB"))
        // shorter aliases stations use for the same purpose
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?file=book.txt"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?name=book.epub&id=1"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/dl?attachment=%E4%B8%89%E5%9B%BD.txt"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/dl?fname=book.TXT"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/down?book=%E6%96%97%E7%BD%97.epub"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?down=novel.txt"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/download/%E4%B8%80%E5%BF%B5%E6%B0%B8%E6%81%92.txt"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/get?file=book.txt"))
    }

    @Test
    fun explicitFileTargetsWinOverBookNamesElsewhereInAddress() {
        for (url in listOf(
            "https://example.com/book.epub?source=other.txt",
            "https://example.com/book.txt?preview=cover.epub",
            "https://example.com/archive.txt/book.epub",
            "https://example.com/archive.epub/book.txt?source=other.epub",
            "https://example.com/get?filename=book.epub&source=other.txt",
        )) {
            assertTrue(url, BrowserDownloadPolicy.isDirectBookUrl(url))
            assertFalse(url, BrowserDownloadPolicy.isInlineReadablePage(url))
        }
        val preview = "https://example.com/book.epub/preview?source=other.txt"
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl(preview))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage(preview))
    }

    @Test
    fun trailingSlashFileTargetsStillDownload() {
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.txt/"))
        assertTrue(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.epub/"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/book.txt/"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.txt/preview/"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/book.txt/preview/"))
    }

    @Test
    fun httpAndJavascriptNeverNavigateOrDownload() {
        for (blocked in listOf(
            "http://example.com/book.txt",
            "HTTP://example.com/book.epub",
            "javascript:alert(1)",
            "JAVASCRIPT:alert(1)",
            "javascript:https://example.com/book.txt",
        )) {
            assertTrue(blocked, BrowserDownloadPolicy.normalizeHttpsAddress(blocked) == null)
            assertFalse(blocked, BrowserDownloadPolicy.allowsHttpsNavigation(blocked))
            assertFalse(blocked, BrowserDownloadPolicy.allowsDownload(blocked))
            assertFalse(blocked, BrowserDownloadPolicy.allowsSubFrameNavigation(blocked))
            assertFalse(blocked, BrowserDownloadPolicy.isDirectBookUrl(blocked))
            assertFalse(blocked, BrowserDownloadPolicy.isInlineReadablePage(blocked))
        }
        assertTrue(BrowserDownloadPolicy.allowsHttpsNavigation("https://example.com/book.txt"))
        assertTrue(BrowserDownloadPolicy.allowsDownload("https://example.com/get?file=book.txt"))
        assertTrue(BrowserDownloadPolicy.allowsSubFrameNavigation("https://example.com/read"))
        assertTrue(BrowserDownloadPolicy.allowsSubFrameNavigation("about:blank"))
        assertTrue(BrowserDownloadPolicy.allowsSubFrameNavigation("ABOUT:blank"))
        assertFalse(BrowserDownloadPolicy.allowsSubFrameNavigation(""))
        assertFalse(BrowserDownloadPolicy.allowsSubFrameNavigation(null))
        assertFalse(BrowserDownloadPolicy.allowsDownload(null))
    }

    @Test
    fun pagesAndUnsafeSchemesRemainNormalNavigation() {
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/books"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.txt/preview"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.epub/preview"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/read/book.epub/3"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?preview=book.txt"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?filename=cover.jpg"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("http://example.com/book.txt"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("javascript:alert(1)"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl(null))
    }

    @Test
    fun nestedRedirectUrlsAreNotMistakenForReadablePages() {
        // 跳转目标里嵌着 .txt：放行，不当本站正文截留。
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/go?redirect=https://other.com/a.txt"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/go?redirect=https://other.com/a.txt"))
    }

    @Test
    fun inlineReadablePagesStayInTabInsteadOfDownloading() {
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/book.txt/preview"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/read/book.txt/3"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/book.epub/preview"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/read/book.epub/3"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/book.epub/preview"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/get?preview=book.txt"))
        assertTrue(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/get?preview=book.epub"))
        assertFalse(BrowserDownloadPolicy.isDirectBookUrl("https://example.com/get?preview=book.epub"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/book.txt"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/BOOK.EPUB?token=1#x"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("https://example.com/books"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage("http://example.com/book.txt/preview"))
        assertFalse(BrowserDownloadPolicy.isInlineReadablePage(null))
    }

    @Test
    fun sameHostGuardsAddressBarReferer() {
        assertTrue(
            BrowserDownloadPolicy.sameHost(
                "https://example.com/books/1",
                "https://example.com/dl/book.txt",
            )
        )
        assertTrue(
            BrowserDownloadPolicy.sameHost(
                "https://EXAMPLE.com/a",
                "https://example.com/b.txt",
            )
        )
        assertFalse(
            BrowserDownloadPolicy.sameHost(
                "https://example.com/books/1",
                "https://other.com/book.txt",
            )
        )
        assertFalse(BrowserDownloadPolicy.sameHost(null, "https://example.com/b.txt"))
        assertFalse(BrowserDownloadPolicy.sameHost("https://example.com/a", null))
        assertFalse(BrowserDownloadPolicy.sameHost("not a url", "https://example.com/b.txt"))
    }

    @Test
    fun recentHistoryKeepsOrdinaryPagesButRejectsVisibleCredentials() {
        assertTrue(
            BrowserDownloadPolicy.historyUrl(" example.com/read?id=123&chapter=4 ") ==
                "https://example.com/read?id=123&chapter=4",
        )
        for (url in listOf(
            "https://reader:secret@example.com/books",
            "https://example.com/read?token=secret&id=1",
            "https://example.com/read?ACCESS_TOKEN=secret",
            "https://example.com/read?to%6ben=secret",
            "https://example.com/read#sessionid=secret",
            "https://example.com/read?x-amz-signature=secret",
        )) {
            assertTrue(url, BrowserDownloadPolicy.historyUrl(url) == null)
        }
    }

    @Test
    fun hostRangeMarksDomainInsideLongUrls() {
        val url = "https://example.com/get?filename=book.txt&token=1"
        val range = BrowserDownloadPolicy.hostRange(url)
        assertTrue(range != null)
        assertTrue(url.substring(range!!.first, range.last + 1) == "example.com")
        assertTrue(BrowserDownloadPolicy.hostRange("http://example.com/x") == null)
        assertTrue(BrowserDownloadPolicy.hostRange("javascript:alert(1)") == null)
        assertTrue(BrowserDownloadPolicy.hostRange(null) == null)
    }

    @Test
    fun hostHighlightUsesOriginalInputAndSkipsUserInfo() {
        for (url in listOf(
            "example.com/books",
            "  example.com/books  ",
            "https://example.com@example.com:8443/books",
            "  HTTPS://example.com@example.com/books  ",
        )) {
            val range = BrowserDownloadPolicy.hostRange(url)!!
            assertTrue(url.substring(range) == "example.com")
            assertTrue(range.first == url.lastIndexOf("example.com"))
        }
        val display = BrowserDownloadPolicy.recentDisplay(
            "https://example.com@example.com:8443/books",
        )
        assertTrue(display.host == "example.com")
        assertTrue(display.path == ":8443/books")
    }

    @Test
    fun downloadEtaNeedsEnoughBytesAndTrustedRate() {
        assertTrue(BrowserDownloadEta.remainingSeconds(0L, 1_000L, 100.0) == null)
        assertTrue(BrowserDownloadEta.remainingSeconds(1_000L, 1_000L, 100.0) == null)
        assertTrue(BrowserDownloadEta.remainingSeconds(64 * 1024L, -1L, 100.0) == null)
        assertTrue(BrowserDownloadEta.remainingSeconds(64 * 1024L, 1_000_000L, 0.0) == null)
        assertTrue(BrowserDownloadEta.remainingSeconds(1_000_000L, 1_000_000L, 50.0) == 0L)
        assertTrue(BrowserDownloadEta.remainingSeconds(500_000L, 1_000_000L, 100_000.0) == 5L)
    }

    @Test
    fun downloadEtaRateFoldsSamplesSmoothly() {
        // First trusted sample seeds the rate.
        assertTrue(BrowserDownloadEta.updateRate(0.0, 100_000L, 1_000L) == 100_000.0)
        // Flat samples converge to the sample, not away.
        val steady = BrowserDownloadEta.updateRate(100_000.0, 100_000L, 1_000L)
        assertTrue(steady == 100_000.0)
        // A 2x spike moves the EMA only partway (0.35 weight).
        val spiked = BrowserDownloadEta.updateRate(100_000.0, 200_000L, 1_000L)
        assertTrue(spiked == 135_000.0)
        // Bad samples keep the previous rate.
        assertTrue(BrowserDownloadEta.updateRate(50_000.0, 0L, 1_000L) == 50_000.0)
        assertTrue(BrowserDownloadEta.updateRate(50_000.0, 100L, 0L) == 50_000.0)
        assertTrue(BrowserDownloadEta.updateRate(50_000.0, -10L, 100L) == 50_000.0)
    }

    @Test
    fun downloadEtaFragmentFormatsSecondsAndMinutes() {
        val sec: (Long) -> String = { v -> "还剩约${v}秒" }
        val min: (Long) -> String = { v -> "还剩约${v}分钟" }
        assertTrue(
            BrowserDownloadEta.etaFragment(500_000L, 1_000_000L, 100_000.0, sec, min) == "还剩约5秒",
        )
        assertTrue(
            BrowserDownloadEta.etaFragment(33 * 1024L, 10_000_000L, 100_000.0, sec, min) == "还剩约2分钟",
        )
        assertTrue(
            BrowserDownloadEta.etaFragment(1_000L, 1_000_000L, 100_000.0, sec, min) == "",
        )
    }

    @Test
    fun recentDisplaySplitsHostAndPath() {
        val long = BrowserDownloadPolicy.recentDisplay(
            "https://example.com/books/1?filename=x.txt&token=abc",
        )
        assertTrue(long.host == "example.com")
        assertTrue(long.path.startsWith("/books/1"))
        val bare = BrowserDownloadPolicy.recentDisplay("https://example.com")
        assertTrue(bare.host == "example.com")
        val broken = BrowserDownloadPolicy.recentDisplay("not a url at all")
        assertTrue(broken.host == "not a url at all")
        assertTrue(broken.path == "")
    }

    @Test
    fun cancelledDownloadCannotClearReplacementSession() {
        val sessions = ImportSessionTracker()
        val cancelled = sessions.start()
        sessions.invalidate()
        val replacement = sessions.start()

        assertFalse(sessions.owns(cancelled))
        assertTrue(sessions.owns(replacement))
    }

    @Test
    fun invalidationRevokesCurrentSessionBeforeReplacementStarts() {
        val sessions = ImportSessionTracker()
        val current = sessions.start()

        sessions.invalidate()

        assertFalse(sessions.owns(current))
    }
}
