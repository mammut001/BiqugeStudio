package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertThrows
import org.junit.Test

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
}
