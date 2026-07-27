package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.Book

/** Progress filter for the shelf list (Material FilterChip). */
enum class ShelfProgressFilter {
    ALL,
    IN_PROGRESS,
    NOT_STARTED,
    FINISHED,
}

/** Sort order for the "全部书籍" section. */
enum class ShelfSortOrder {
    DEFAULT,
    TITLE,
    PROGRESS_DESC,
    PROGRESS_ASC,
}

/**
 * Pure shelf list helpers — unit-tested without Compose.
 * Position is 0..1000 (LibraryStore scale).
 */
object ShelfFilters {
    /**
     * In-progress books (position in (0, 1000)), sorted with [order].
     * DEFAULT keeps LibraryStore order among matches.
     */
    fun continueReading(
        books: List<Book>,
        order: ShelfSortOrder = ShelfSortOrder.DEFAULT,
    ): List<Book> =
        sorted(
            books.filter { it.position > 0 && it.position < 1000 },
            order,
        )

    fun matchesFilter(book: Book, filter: ShelfProgressFilter): Boolean = when (filter) {
        ShelfProgressFilter.ALL -> true
        ShelfProgressFilter.IN_PROGRESS -> book.position > 0 && book.position < 1000
        ShelfProgressFilter.NOT_STARTED -> book.position <= 0
        ShelfProgressFilter.FINISHED -> book.position >= 1000
    }

    fun filtered(books: List<Book>, filter: ShelfProgressFilter): List<Book> =
        books.filter { matchesFilter(it, filter) }

    fun sorted(books: List<Book>, order: ShelfSortOrder): List<Book> = when (order) {
        ShelfSortOrder.DEFAULT -> books
        ShelfSortOrder.TITLE -> books.sortedBy { it.title.lowercase() }
        ShelfSortOrder.PROGRESS_DESC -> books.sortedByDescending { it.position }
        ShelfSortOrder.PROGRESS_ASC -> books.sortedBy { it.position }
    }

    fun sectionAll(books: List<Book>, filter: ShelfProgressFilter, order: ShelfSortOrder): List<Book> =
        sorted(filtered(books, filter), order)
}
