package app.maoyankanshu.novel.selfuse;

/** A locally owned text book. No remote catalogue is required. */
public final class Book {
    public final String id;
    public final String title;
    public final String author;
    public final String text;
    public int position;
    /**
     * Absolute path to an app-private cover image file, or null.
     * Optional; existing 5-arg constructor keeps {@code null} for TXT / no-cover books.
     */
    public final String coverPath;

    public Book(String id, String title, String author, String text, int position) {
        this(id, title, author, text, position, null);
    }

    public Book(String id, String title, String author, String text, int position, String coverPath) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.text = text;
        this.position = position;
        this.coverPath = coverPath;
    }

    public String progressLabel() {
        if (position <= 0) return "未开始";
        if (position >= 1000) return "已读完";
        return "已读 " + Math.round(position / 10f) + "%";
    }
}
