package app.maoyankanshu.novel.selfuse;

import android.content.Context;

/** One authoritative cascade for deleting all per-book device-local state. */
public final class BookDataDeletion {
    private BookDataDeletion() { }

    public static void remove(Context context, String bookId) {
        if (context == null || bookId == null || bookId.isEmpty()) return;
        LibraryStore.get(context).remove(bookId);
        ReadingHistory.get(context).remove(bookId);
        BookmarkStore.get(context).clear(bookId);
    }
}
