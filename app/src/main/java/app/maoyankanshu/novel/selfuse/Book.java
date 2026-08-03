package app.maoyankanshu.novel.selfuse;

/** A locally owned text book. No remote catalogue is required. */
public final class Book {
    public final String id;
    public final String title;
    public final String author;
    /**
     * Full body when loaded for reading/export; empty string for shelf/list rows
     * built via [LibraryStore.booksForListing] (body lives on disk only).
     */
    public final String text;
    public int position;
    /**
     * Absolute path to an app-private cover image file, or null.
     * Optional; existing 5-arg constructor keeps {@code null} for TXT / no-cover books.
     */
    public final String coverPath;
    /**
     * Character count of the body for list/overview UI when [text] is empty.
     * When [text] is non-empty, [bodyLength] prefers [text].length().
     */
    public final int textLength;

    public Book(String id, String title, String author, String text, int position) {
        this(id, title, author, text, position, null);
    }

    public Book(String id, String title, String author, String text, int position, String coverPath) {
        this(id, title, author, text, position, coverPath, -1);
    }

    /**
     * @param textLength explicit body length for list rows (−1 → derive from [text])
     */
    public Book(
            String id,
            String title,
            String author,
            String text,
            int position,
            String coverPath,
            int textLength
    ) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.text = text == null ? "" : text;
        this.position = position;
        this.coverPath = coverPath;
        if (textLength >= 0) {
            this.textLength = textLength;
        } else {
            this.textLength = this.text.length();
        }
    }

    /**
     * Body character count for discover/overview without requiring a loaded [text].
     */
    public int bodyLength() {
        if (text != null && !text.isEmpty()) {
            return text.length();
        }
        return Math.max(0, textLength);
    }

    public String progressLabel() {
        if (position <= 0) return "未开始";
        if (position >= 1000) return "已读完";
        return "已读 " + Math.round(position / 10f) + "%";
    }
}
