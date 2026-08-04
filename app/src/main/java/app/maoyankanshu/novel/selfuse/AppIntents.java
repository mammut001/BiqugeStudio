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
        return new Intent(context, SearchActivity.class)
                .putExtra(SearchActivity.EXTRA_IMPORT, true);
    }

    /** ACTION_VIEW for a content:// or file:// TXT/EPUB URI (tests + deep links). */
    public static Intent viewBook(Context context, android.net.Uri uri) {
        return new Intent(context, SearchActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }

    /** ACTION_SEND with EXTRA_STREAM content URI (share-sheet style). */
    public static Intent sendBookStream(Context context, android.net.Uri streamUri, String mimeType) {
        Intent intent = new Intent(context, SearchActivity.class)
                .setAction(Intent.ACTION_SEND)
                .setType(mimeType != null ? mimeType : "*/*")
                .putExtra(Intent.EXTRA_STREAM, streamUri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }

    /**
     * ACTION_SEND_MULTIPLE with EXTRA_STREAM ArrayList&lt;Uri&gt; (capped at
     * {@link ImportIntentUris#MAX_URIS} when handled).
     */
    public static Intent sendBookStreams(
            Context context,
            java.util.ArrayList<android.net.Uri> streamUris,
            String mimeType
    ) {
        Intent intent = new Intent(context, SearchActivity.class)
                .setAction(Intent.ACTION_SEND_MULTIPLE)
                .setType(mimeType != null ? mimeType : "*/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, streamUris)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
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

    public static Intent remoteImport(Context context) {
        return new Intent(context, RemoteImportActivity.class);
    }

    public static Intent webImport(Context context) {
        return new Intent(context, WebImportActivity.class);
    }
}
