package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for shipped [LibraryListModels] — discover/shelf aggregates must work
 * on metadata-only rows (empty [Book.text], explicit [Book.textLength]).
 */
class LibraryListModelsTest {

    @Test
    fun totalCharacters_usesBodyLengthNotLoadedText() {
        val books = listOf(
            Book("1", "A", "x", "", 0, null, 1_000_000),
            Book("2", "B", "y", "short", 10, null),
            Book("3", "C", "z", "", 0, null, 2_500),
        )
        assertEquals(1_000_000 + 5 + 2_500, LibraryListModels.totalCharacters(books))
        assertTrue(LibraryListModels.isMetadataOnlyRow(books[0]))
        assertFalse(LibraryListModels.isMetadataOnlyRow(books[1]))
    }

    @Test
    fun startedAndInProgress_matchShelfProgressScale() {
        val books = listOf(
            Book("a", "未开始", "x", "", 0, null, 100),
            Book("b", "在读", "y", "", 500, null, 100),
            Book("c", "已读完", "z", "", 1000, null, 100),
        )
        assertEquals(2, LibraryListModels.startedCount(books))
        val mid = LibraryListModels.inProgressBooks(books)
        assertEquals(1, mid.size)
        assertEquals("在读", mid[0].title)
        assertEquals(500, mid[0].position)
    }

    @Test
    fun multiMbClassListing_doesNotRequireMaterializingBodyInModel() {
        val length = 3_000_000
        val row = Book("big", "大书", "作者", "", 750, null, length)
        assertEquals("", row.text)
        assertEquals(length, row.bodyLength())
        assertEquals(length, LibraryListModels.totalCharacters(listOf(row)))
        assertEquals("已读 75%", row.progressLabel())
        assertTrue(LibraryListModels.isMetadataOnlyRow(row))
    }
}
