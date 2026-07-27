package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.Intent;

/**
 * Java-side Intent factory so Compose/Kotlin avoids {@code SomeActivity::class.java}
 * resolution bugs with certain SDK 36 + Kotlin FIR combinations.
 */
public final class AppIntents {
    private AppIntents() { }

    public static Intent search(Context context) {
        return new Intent(context, SearchActivity.class);
    }

    public static Intent importLocal(Context context) {
        return new Intent(context, SearchActivity.class).putExtra(SearchActivity.EXTRA_IMPORT, true);
    }

    public static Intent bookDetail(Context context, String bookId) {
        return new Intent(context, BookDetailActivity.class).putExtra(BookDetailActivity.EXTRA_ID, bookId);
    }

    public static Intent bookDetailEdit(Context context, String bookId) {
        return bookDetail(context, bookId).putExtra(BookDetailActivity.EXTRA_EDIT, true);
    }

    public static Intent reader(Context context, String bookId) {
        return new Intent(context, ReaderActivity.class).putExtra(ReaderActivity.EXTRA_ID, bookId);
    }

    public static Intent legacyReader(Context context, String bookId) {
        return new Intent(context, LegacyReaderActivity.class).putExtra(LegacyReaderActivity.EXTRA_ID, bookId);
    }

    public static Intent remoteImport(Context context) {
        return new Intent(context, RemoteImportActivity.class);
    }

    public static Intent webImport(Context context) {
        return new Intent(context, WebImportActivity.class);
    }
}
