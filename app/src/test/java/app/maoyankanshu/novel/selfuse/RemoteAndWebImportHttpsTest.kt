package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class RemoteAndWebImportHttpsTest {

    @Test
    fun remoteImportDownloader_rejectsHttpUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteImportDownloader.download(
                rawUrl = "http://example.com/book.epub",
                preferredTitle = "",
                userAgent = "TestAgent",
                defaultEpubTitle = "EPUB",
                defaultTxtTitle = "TXT",
                authorEpub = "Author",
                authorTxt = "Author",
            )
        }
    }

    @Test
    fun remoteImportDownloader_rejectsNonHttpsUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteImportDownloader.download(
                rawUrl = "ftp://example.com/book.txt",
                preferredTitle = "",
                userAgent = "TestAgent",
                defaultEpubTitle = "EPUB",
                defaultTxtTitle = "TXT",
                authorEpub = "Author",
                authorTxt = "Author",
            )
        }
    }

    @Test
    fun webImportFetcher_rejectsHttpUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            WebImportFetcher.fetch(
                rawUrl = "http://example.com/article",
                preferredTitle = "",
                userAgent = "TestAgent",
                defaultTitle = "Title",
            )
        }
    }

    @Test
    fun webImportFetcher_rejectsNonHttpsUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            WebImportFetcher.fetch(
                rawUrl = "file:///tmp/test.html",
                preferredTitle = "",
                userAgent = "TestAgent",
                defaultTitle = "Title",
            )
        }
    }

    // --- Content-Length / size guards (JVM, no network) ---

    @Test
    fun parseContentLength_prefersHeaderOverField() {
        assertEquals(100L, HttpsBodyLimits.parseContentLength("100", 5))
        assertEquals(50L, HttpsBodyLimits.parseContentLength(" 50 ", -1))
    }

    @Test
    fun parseContentLength_fallsBackToContentLengthField() {
        assertEquals(42L, HttpsBodyLimits.parseContentLength(null, 42))
        assertEquals(7L, HttpsBodyLimits.parseContentLength("not-a-number", 7))
    }

    @Test
    fun parseContentLength_unknownWhenMissing() {
        assertEquals(-1L, HttpsBodyLimits.parseContentLength(null, -1))
        assertEquals(-1L, HttpsBodyLimits.parseContentLength("", -1))
        assertEquals(-1L, HttpsBodyLimits.parseContentLength("abc", -1))
    }

    @Test
    fun parseContentLength_acceptsLargeHeaderBeyondInt() {
        val large = Int.MAX_VALUE.toLong() + 10L
        assertEquals(large, HttpsBodyLimits.parseContentLength(large.toString(), -1))
    }

    @Test
    fun rejectIfDeclaredTooLarge_rejectsRemoteOverMax() {
        val ex = assertThrows(IllegalStateException::class.java) {
            HttpsBodyLimits.rejectIfDeclaredTooLarge(
                RemoteImportDownloader.MAX_BYTES.toLong() + 1L,
                RemoteImportDownloader.MAX_BYTES,
            )
        }
        assertTrue(ex.message?.contains("too large") == true)
    }

    @Test
    fun rejectIfDeclaredTooLarge_rejectsWebOverMax() {
        val ex = assertThrows(IllegalStateException::class.java) {
            HttpsBodyLimits.rejectIfDeclaredTooLarge(
                WebImportFetcher.MAX_BYTES.toLong() + 1L,
                WebImportFetcher.MAX_BYTES,
            )
        }
        assertTrue(ex.message?.contains("too large") == true)
    }

    @Test
    fun rejectIfDeclaredTooLarge_allowsExactMaxAndUnknown() {
        HttpsBodyLimits.rejectIfDeclaredTooLarge(
            RemoteImportDownloader.MAX_BYTES.toLong(),
            RemoteImportDownloader.MAX_BYTES,
        )
        HttpsBodyLimits.rejectIfDeclaredTooLarge(
            WebImportFetcher.MAX_BYTES.toLong(),
            WebImportFetcher.MAX_BYTES,
        )
        // Unknown Content-Length (-1): allow; stream read still enforces the cap.
        HttpsBodyLimits.rejectIfDeclaredTooLarge(-1L, RemoteImportDownloader.MAX_BYTES)
        HttpsBodyLimits.rejectIfDeclaredTooLarge(-1L, WebImportFetcher.MAX_BYTES)
    }

    @Test
    fun readAll_rejectsWhenStreamExceedsMax() {
        val payload = ByteArray(100) { 1 }
        val ex = assertThrows(IllegalStateException::class.java) {
            HttpsBodyLimits.readAll(ByteArrayInputStream(payload), maxBytes = 50)
        }
        assertTrue(ex.message?.contains("too large") == true)
    }

    @Test
    fun readAll_returnsBodyWhenWithinMax() {
        val payload = byteArrayOf(1, 2, 3, 4, 5)
        val out = HttpsBodyLimits.readAll(ByteArrayInputStream(payload), maxBytes = 50)
        assertArrayEquals(payload, out)
    }

    @Test
    fun maxBytes_matchDocumentedLimits() {
        assertEquals(50 * 1024 * 1024, RemoteImportDownloader.MAX_BYTES)
        assertEquals(12 * 1024 * 1024, WebImportFetcher.MAX_BYTES)
        assertEquals(RemoteImportDownloader.MAX_BYTES, HttpsBodyLimits.REMOTE_MAX_BYTES)
        assertEquals(WebImportFetcher.MAX_BYTES, HttpsBodyLimits.WEB_MAX_BYTES)
    }
}
