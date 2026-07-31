package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfFiltersTest {

    private fun book(
        id: String,
        title: String,
        position: Int,
        author: String = "author",
    ): Book = Book(id, title, author, "text", position)

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
    fun continueReading_respects_sort_order() {
        val books = listOf(
            book("a", "Zebra", 0),
            book("b", "Mango", 100),
            book("c", "Apple", 900),
            book("d", "Done", 1000),
            book("e", "Berry", 200),
        )
        // DEFAULT: LibraryStore order among in-progress only
        assertEquals(
            listOf("b", "c", "e"),
            ShelfFilters.continueReading(books, ShelfSortOrder.DEFAULT).map { it.id },
        )
        assertEquals(
            listOf("Apple", "Berry", "Mango"),
            ShelfFilters.continueReading(books, ShelfSortOrder.TITLE).map { it.title },
        )
        assertEquals(
            listOf(900, 200, 100),
            ShelfFilters.continueReading(books, ShelfSortOrder.PROGRESS_DESC).map { it.position },
        )
        assertEquals(
            listOf(100, 200, 900),
            ShelfFilters.continueReading(books, ShelfSortOrder.PROGRESS_ASC).map { it.position },
        )
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

    @Test
    fun groupByAuthor_preservesOrderAndFirstSeenAuthors() {
        val books = listOf(
            book("1", "A1", 0, author = "Bob"),
            book("2", "A2", 0, author = "Alice"),
            book("3", "A3", 0, author = "Bob"),
            book("4", "A4", 0, author = "Alice"),
        )
        val groups = ShelfFilters.groupByAuthor(books, unknownAuthorLabel = "未知作者")
        assertEquals(listOf("Bob", "Alice"), groups.map { it.authorLabel })
        assertEquals(listOf("A1", "A3"), groups[0].books.map { it.title })
        assertEquals(listOf("A2", "A4"), groups[1].books.map { it.title })
    }

    @Test
    fun groupByAuthor_blankAuthor_usesFallback() {
        val books = listOf(
            book("1", "T1", 0, author = "  "),
            book("2", "T2", 0, author = ""),
            book("3", "T3", 0, author = "Named"),
        )
        val groups = ShelfFilters.groupByAuthor(books, unknownAuthorLabel = "未知作者")
        assertEquals(2, groups.size)
        assertEquals("未知作者", groups[0].authorLabel)
        assertEquals(listOf("T1", "T2"), groups[0].books.map { it.title })
        assertEquals("Named", groups[1].authorLabel)
        assertEquals(listOf("T3"), groups[1].books.map { it.title })
    }

    @Test
    fun groupByAuthor_emptyList() {
        assertTrue(ShelfFilters.groupByAuthor(emptyList(), "未知作者").isEmpty())
    }

    @Test
    fun groupByAuthor_afterSectionAll_keepsSortWithinAuthor() {
        val books = listOf(
            book("1", "Zebra", 100, author = "A"),
            book("2", "Apple", 900, author = "B"),
            book("3", "Mango", 50, author = "A"),
            book("4", "Berry", 200, author = "B"),
        )
        val flat = ShelfFilters.sectionAll(
            books,
            ShelfProgressFilter.ALL,
            ShelfSortOrder.TITLE,
        )
        // Title order: Apple, Berry, Mango, Zebra — first-seen authors: B then A
        val groups = ShelfFilters.groupByAuthor(flat, "未知作者")
        assertEquals(listOf("B", "A"), groups.map { it.authorLabel })
        assertEquals(listOf("Apple", "Berry"), groups[0].books.map { it.title })
        assertEquals(listOf("Mango", "Zebra"), groups[1].books.map { it.title })
    }
}
