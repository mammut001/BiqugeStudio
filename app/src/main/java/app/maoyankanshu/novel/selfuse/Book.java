package app.maoyankanshu.novel.selfuse;

/** A locally owned text book. No remote catalogue is required. */
public final class Book {
    public final String id;
    public final String title;
    public final String author;
    public final String text;
    public int position;

    Book(String id, String title, String author, String text, int position) {
        this.id = id; this.title = title; this.author = author; this.text = text; this.position = position;
    }

    public String progressLabel() {
        if (position <= 0) return "未开始";
        if (position >= 1000) return "已读完";
        return "已读 " + Math.round(position / 10f) + "%";
    }
}
