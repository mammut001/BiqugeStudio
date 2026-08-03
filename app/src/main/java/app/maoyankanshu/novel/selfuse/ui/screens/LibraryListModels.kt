package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.Book

/**
 * Pure presentation aggregates for shelf / store / discover list models.
 * Uses [Book.bodyLength] so list rows need not hold multi‑MB [Book.text].
 */
object LibraryListModels {

    /** Total characters across the library (discover “本地文本” stat). */
    fun totalCharacters(books: List<Book>): Int {
        var sum = 0
        for (book in books) {
            val len = book.bodyLength().coerceAtLeast(0)
            if (sum > Int.MAX_VALUE - len) return Int.MAX_VALUE
            sum += len
        }
        return sum
    }

    /** Books with any progress saved (position &gt; 0). */
    fun startedCount(books: List<Book>): Int =
        books.count { it.position > 0 }

    /** Books mid-read (0 &lt; position &lt; 1000). */
    fun inProgressBooks(books: List<Book>): List<Book> =
        books.filter { it.position > 0 && it.position < 1000 }

    /** Whether the list model is a light row (no body payload). */
    fun isMetadataOnlyRow(book: Book): Boolean =
        book.text.isEmpty() && book.bodyLength() >= 0
}
