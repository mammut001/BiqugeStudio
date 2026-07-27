package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfFiltersTest {

    private fun book(id: String, title: String, position: Int): Book =
        Book(id, title, "author", "text", position)

    @Test
    fun continueReading_only_in_progress() {
        val books = listOf(
            book("a", "A", 0),
            book("b", "B", 500),
            book("c", "C", 1000),
            book("d", "D", 1),
        )
        val cont = ShelfFilters.continueReading(books)
        assertEquals(listOf("b", "d"), cont.map { it.id })
    }

    @Test
    fun filter_progress_buckets() {
        val books = listOf(
            book("a", "A", 0),
            book("b", "B", 500),
            book("c", "C", 1000),
        )
        assertEquals(3, ShelfFilters.filtered(books, ShelfProgressFilter.ALL).size)
        assertEquals(listOf("b"), ShelfFilters.filtered(books, ShelfProgressFilter.IN_PROGRESS).map { it.id })
        assertEquals(listOf("a"), ShelfFilters.filtered(books, ShelfProgressFilter.NOT_STARTED).map { it.id })
        assertEquals(listOf("c"), ShelfFilters.filtered(books, ShelfProgressFilter.FINISHED).map { it.id })
    }

    @Test
    fun sort_by_title_and_progress() {
        val books = listOf(
            book("1", "Zebra", 100),
            book("2", "Apple", 900),
            book("3", "Mango", 50),
        )
        assertEquals(
            listOf("Apple", "Mango", "Zebra"),
            ShelfFilters.sorted(books, ShelfSortOrder.TITLE).map { it.title },
        )
        assertEquals(
            listOf(900, 100, 50),
            ShelfFilters.sorted(books, ShelfSortOrder.PROGRESS_DESC).map { it.position },
        )
        assertEquals(
            listOf(50, 100, 900),
            ShelfFilters.sorted(books, ShelfSortOrder.PROGRESS_ASC).map { it.position },
        )
        assertTrue(ShelfFilters.sorted(books, ShelfSortOrder.DEFAULT) === books ||
            ShelfFilters.sorted(books, ShelfSortOrder.DEFAULT).map { it.id } == books.map { it.id })
    }

    @Test
    fun sectionAll_combines_filter_and_sort() {
        val books = listOf(
            book("1", "Zebra", 0),
            book("2", "Apple", 500),
            book("3", "Mango", 1000),
            book("4", "Berry", 200),
        )
        val result = ShelfFilters.sectionAll(
            books,
            ShelfProgressFilter.IN_PROGRESS,
            ShelfSortOrder.TITLE,
        )
        assertEquals(listOf("Apple", "Berry"), result.map { it.title })
    }
}
