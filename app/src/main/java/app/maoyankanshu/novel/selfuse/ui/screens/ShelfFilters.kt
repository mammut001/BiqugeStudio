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
    RECENT,
    PROGRESS_DESC,
    PROGRESS_ASC,
}

/**
 * Optional grouping for the "全部书籍" section only.
 * Default [NONE] keeps a flat list (source-compatible behavior).
 */
enum class ShelfGroupMode {
    NONE,
    BY_AUTHOR,
}

/** One author heading + books beneath it (order of books preserved from input). */
data class ShelfAuthorGroup(
    val authorLabel: String,
    val books: List<Book>,
)

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
        recentReadAt: Map<String, Long> = emptyMap(),
    ): List<Book> =
        sorted(
            books.filter { it.position > 0 && it.position < 1000 },
            order,
            recentReadAt,
        )

    fun matchesFilter(book: Book, filter: ShelfProgressFilter): Boolean = when (filter) {
        ShelfProgressFilter.ALL -> true
        ShelfProgressFilter.IN_PROGRESS -> book.position > 0 && book.position < 1000
        ShelfProgressFilter.NOT_STARTED -> book.position <= 0
        ShelfProgressFilter.FINISHED -> book.position >= 1000
    }

    fun filtered(books: List<Book>, filter: ShelfProgressFilter): List<Book> =
        books.filter { matchesFilter(it, filter) }

    fun sorted(
        books: List<Book>,
        order: ShelfSortOrder,
        recentReadAt: Map<String, Long> = emptyMap(),
    ): List<Book> = when (order) {
        ShelfSortOrder.DEFAULT -> books
        ShelfSortOrder.TITLE -> books.sortedBy { it.title.lowercase() }
        // Kotlin's stable sort preserves shelf order for books with the same timestamp;
        // books never opened are kept after known history in their original order.
        ShelfSortOrder.RECENT -> books.sortedByDescending { recentReadAt[it.id] ?: Long.MIN_VALUE }
        ShelfSortOrder.PROGRESS_DESC -> books.sortedByDescending { it.position }
        ShelfSortOrder.PROGRESS_ASC -> books.sortedBy { it.position }
    }

    fun sectionAll(
        books: List<Book>,
        filter: ShelfProgressFilter,
        order: ShelfSortOrder,
        recentReadAt: Map<String, Long> = emptyMap(),
    ): List<Book> = sorted(filtered(books, filter), order, recentReadAt)

    /**
     * Group an already-filtered/sorted list by author for display.
     * - Preserves relative book order within each group and first-seen author order.
     * - Blank/whitespace authors use [unknownAuthorLabel] (localized by UI).
     * Does not re-sort or re-filter; call after [sectionAll].
     */
    fun groupByAuthor(
        books: List<Book>,
        unknownAuthorLabel: String,
    ): List<ShelfAuthorGroup> {
        if (books.isEmpty()) return emptyList()
        val fallback = unknownAuthorLabel.ifBlank { "—" }
        val groups = LinkedHashMap<String, MutableList<Book>>()
        for (book in books) {
            val label = book.author.trim().ifEmpty { fallback }
            groups.getOrPut(label) { ArrayList() }.add(book)
        }
        return groups.map { (label, list) -> ShelfAuthorGroup(label, list) }
    }
}
