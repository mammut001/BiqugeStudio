package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for remote import type detection and safe title parsing
 * (no network). Covers final-URL extension, MIME case/parameters, Content-Disposition,
 * and injection / path rejection.
 */
class RemoteImportDownloaderTypeTitleTest {

    // --- Type detection: URL extension + Content-Type ---

    @Test
    fun detectIsEpub_finalUrlLowercaseExtension() {
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/books/novel.epub",
                null,
            ),
        )
    }

    @Test
    fun detectIsEpub_finalUrlUppercaseExtension() {
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/files/BOOK.EPUB",
                "text/plain",
            ),
        )
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/files/Mixed.EpUb?token=1",
                null,
            ),
        )
    }

    @Test
    fun detectIsEpub_mimeOnly_caseInsensitive() {
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/download/123",
                "application/epub+zip",
            ),
        )
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/download/123",
                "APPLICATION/EPUB+ZIP",
            ),
        )
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/download/123",
                "Application/Epub+Zip",
            ),
        )
    }

    @Test
    fun detectIsEpub_mimeWithParameters() {
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/get",
                "application/epub+zip; charset=binary",
            ),
        )
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/get",
                "APPLICATION/EPUB+ZIP; name=book.epub",
            ),
        )
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/get",
                "  application/epub+zip ; boundary=x  ",
            ),
        )
    }

    @Test
    fun detectIsEpub_usesFinalUrlNotHostOrQueryFalsePositives() {
        // Host containing "epub" must not force EPUB when path/MIME say otherwise.
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://epub.example.com/story.txt",
                "text/plain",
            ),
        )
        // Query string containing .epub must not force EPUB.
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/download?file=book.epub",
                "text/plain",
            ),
        )
        // Path segment must end with .epub, not merely contain it mid-name without suffix logic.
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/epub-guide.txt",
                "text/plain",
            ),
        )
    }

    @Test
    fun detectIsEpub_rejectsNonEpubMimeAndPath() {
        assertFalse(RemoteImportDownloader.detectIsEpub(null, null))
        assertFalse(RemoteImportDownloader.detectIsEpub("", null))
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/a.txt",
                "text/plain",
            ),
        )
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/a.zip",
                "application/zip",
            ),
        )
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/a",
                "application/octet-stream",
            ),
        )
        // Substring "epub" in unrelated MIME must not match (strict type/subtype).
        assertFalse(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/a",
                "application/x-epub-hint",
            ),
        )
    }

    @Test
    fun detectIsEpub_extensionWinsOverMismatchedMime() {
        assertTrue(
            RemoteImportDownloader.detectIsEpub(
                "https://cdn.example.com/x.EPUB",
                "application/octet-stream",
            ),
        )
    }

    // --- URL path filename ---

    @Test
    fun urlPathFileName_stripsQueryFragmentAndHost() {
        assertEquals(
            "Book.EPUB",
            RemoteImportDownloader.urlPathFileName(
                "https://cdn.example.com/lib/Book.EPUB?sig=1#frag",
            ),
        )
        assertEquals(
            "story.txt",
            RemoteImportDownloader.urlPathFileName("https://example.com/story.txt"),
        )
        assertNull(RemoteImportDownloader.urlPathFileName("https://example.com/"))
        assertNull(RemoteImportDownloader.urlPathFileName(null))
        assertNull(RemoteImportDownloader.urlPathFileName(""))
    }

    // --- Content-Disposition + safe title ---

    @Test
    fun parseSafeFilenameFromContentDisposition_quotedAndBare() {
        assertEquals(
            "My Novel",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"My Novel.epub\"",
            ),
        )
        assertEquals(
            "plain",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=plain.txt",
            ),
        )
        assertEquals(
            "Title",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "inline; FILENAME=\"Title.EPUB\"",
            ),
        )
    }

    @Test
    fun parseSafeFilenameFromContentDisposition_rfc5987FilenameStar() {
        assertEquals(
            "中文书名",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename*=UTF-8''%E4%B8%AD%E6%96%87%E4%B9%A6%E5%90%8D.epub",
            ),
        )
        assertEquals(
            "hello world",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename*=utf-8''hello%20world.txt",
            ),
        )
    }

    @Test
    fun parseSafeFilenameFromContentDisposition_prefersFilenameStar() {
        assertEquals(
            "star-name",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"legacy.epub\"; filename*=UTF-8''star-name.epub",
            ),
        )
    }

    @Test
    fun parseSafeFilenameFromContentDisposition_rejectsInjectionAndPaths() {
        // CR/LF header injection — reject entire header.
        assertNull(
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"safe.epub\"\r\nSet-Cookie: evil=1",
            ),
        )
        assertNull(
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"bad\nname.epub\"",
            ),
        )
        // Path-like values collapse to basename only (no directory text exposed).
        assertEquals(
            "passwd",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"../../../etc/passwd\"",
            ),
        )
        assertEquals(
            "book",
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"C:\\\\Users\\\\x\\\\book.epub\"",
            ),
        )
        assertNull(RemoteImportDownloader.parseSafeFilenameFromContentDisposition(null))
        assertNull(RemoteImportDownloader.parseSafeFilenameFromContentDisposition(""))
        assertNull(RemoteImportDownloader.parseSafeFilenameFromContentDisposition("inline"))
        assertNull(
            RemoteImportDownloader.parseSafeFilenameFromContentDisposition(
                "attachment; filename=\"\"",
            ),
        )
    }

    @Test
    fun parseSafeFilenameFromUrl_stripsPathQueryAndExtension() {
        assertEquals(
            "My_Book",
            RemoteImportDownloader.parseSafeFilenameFromUrl(
                "https://cdn.example.com/a/b/My_Book.EPUB?x=1",
            ),
        )
        assertEquals(
            "notes",
            RemoteImportDownloader.parseSafeFilenameFromUrl(
                "https://cdn.example.com/notes.txt#section",
            ),
        )
        // Query must not supply the title: path segment only (never secret.epub from ?file=).
        assertEquals(
            "download",
            RemoteImportDownloader.parseSafeFilenameFromUrl(
                "https://cdn.example.com/download?file=secret.epub",
            ),
        )
        assertNull(
            RemoteImportDownloader.parseSafeFilenameFromUrl(
                "https://cdn.example.com/?file=secret.epub",
            ),
        )
        assertNull(RemoteImportDownloader.parseSafeFilenameFromUrl("https://cdn.example.com/"))
    }

    @Test
    fun sanitizeToTitle_rejectsControlAndEmpty() {
        assertNull(RemoteImportDownloader.sanitizeToTitle(null))
        assertNull(RemoteImportDownloader.sanitizeToTitle(""))
        assertNull(RemoteImportDownloader.sanitizeToTitle("   "))
        assertNull(RemoteImportDownloader.sanitizeToTitle("."))
        assertNull(RemoteImportDownloader.sanitizeToTitle(".."))
        assertNull(RemoteImportDownloader.sanitizeToTitle("a\u0000b.txt"))
        assertEquals("ok", RemoteImportDownloader.sanitizeToTitle("ok.epub"))
        assertEquals("ok", RemoteImportDownloader.sanitizeToTitle("ok"))
    }

    // --- resolveFallbackTitle priority ---

    @Test
    fun resolveFallbackTitle_contentDispositionThenUrlThenFallback() {
        assertEquals(
            "From Header",
            RemoteImportDownloader.resolveFallbackTitle(
                contentDisposition = "attachment; filename=\"From Header.epub\"",
                finalUrl = "https://cdn.example.com/FromUrl.epub",
                fallback = "Fallback",
            ),
        )
        assertEquals(
            "FromUrl",
            RemoteImportDownloader.resolveFallbackTitle(
                contentDisposition = null,
                finalUrl = "https://cdn.example.com/path/FromUrl.TXT",
                fallback = "Fallback",
            ),
        )
        assertEquals(
            "Fallback",
            RemoteImportDownloader.resolveFallbackTitle(
                contentDisposition = "inline",
                finalUrl = "https://cdn.example.com/",
                fallback = "Fallback",
            ),
        )
        // Injected disposition is skipped; URL still used.
        assertEquals(
            "real",
            RemoteImportDownloader.resolveFallbackTitle(
                contentDisposition = "attachment; filename=\"x\"\r\nEvil: 1",
                finalUrl = "https://cdn.example.com/real.epub",
                fallback = "Fallback",
            ),
        )
    }
}
