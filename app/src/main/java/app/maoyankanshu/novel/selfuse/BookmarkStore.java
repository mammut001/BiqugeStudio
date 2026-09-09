package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Per-book device-local bookmarks, stored independently from reading progress. */
public final class BookmarkStore {
    private static final String PREFS = "bookmarks";
    private final android.content.SharedPreferences prefs;

    private BookmarkStore(Context context) { prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    /** Package-visible storage injection for JVM tests. */
    BookmarkStore(android.content.SharedPreferences prefs) { this.prefs = prefs; }
    public static BookmarkStore get(Context context) { return new BookmarkStore(context); }

    public List<Bookmark> list(String bookId) {
        List<Bookmark> result = new ArrayList<>();
        if (bookId == null || bookId.isEmpty()) return result;
        String raw = prefs.getString(bookId, "");
        if (raw == null || raw.isEmpty()) return result;
        for (String row : raw.split("\\n", -1)) {
            String[] item = row.split("\\|", 3);
            if (item.length < 2) continue;
            try {
                int progress = Integer.parseInt(item[0]);
                String label = decode(item[1]);
                int offset = -1;
                if (item.length >= 3 && !item[2].isEmpty()) {
                    try { offset = Integer.parseInt(item[2]); } catch (NumberFormatException ignored) { offset = -1; }
                    if (offset < 0) offset = -1;
                }
                result.add(new Bookmark(progress, label, offset));
            } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    public void add(String bookId, int progress, String label) {
        add(bookId, progress, label, -1);
    }

    /** [offset] is the char offset in the book body for excerpt display; -1 = unknown (legacy). */
    public void add(String bookId, int progress, String label, int offset) {
        if (bookId == null || bookId.isEmpty()) return;
        List<Bookmark> all = list(bookId);
        String safeLabel = label == null ? "" : label;
        all.add(0, new Bookmark(Math.max(0, Math.min(1000, progress)), safeLabel, offset < 0 ? -1 : offset));
        while (all.size() > 30) all.remove(all.size() - 1);
        save(bookId, all);
    }
    public void remove(String bookId, int index) { List<Bookmark> all = list(bookId); if (index >= 0 && index < all.size()) { all.remove(index); save(bookId, all); } }
    /** Remove every bookmark owned by one deleted book. Other books stay untouched. */
    public void clear(String bookId) {
        if (bookId == null || bookId.isEmpty()) return;
        prefs.edit().remove(bookId).apply();
    }
    private void save(String bookId, List<Bookmark> all) {
        if (all.isEmpty()) {
            clear(bookId);
            return;
        }
        StringBuilder out = new StringBuilder();
        for (Bookmark b : all) out.append(b.progress).append('|').append(encode(b.label)).append('|').append(b.offset).append('\n');
        prefs.edit().putString(bookId, out.toString()).apply();
    }
    private static String encode(String value) { return TextBase64.encode(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(TextBase64.decode(value), StandardCharsets.UTF_8); }
    public static final class Bookmark {
        public final int progress;
        public final String label;
        /** Char offset in the body for excerpt; -1 when recorded before excerpts existed. */
        public final int offset;
        Bookmark(int progress, String label) { this(progress, label, -1); }
        Bookmark(int progress, String label, int offset) { this.progress = progress; this.label = label; this.offset = offset; }
    }

    /**
     * Short excerpt around [offset] in [body] for list display. Whitespace-collapsed,
     * bounded to ~76 chars so the 2-line supporting row shows it whole instead of
     * ellipsizing a longer string. Pure helper for UI/tests.
     */
    public static String excerpt(String body, int offset) {
        if (body == null || body.isEmpty() || offset < 0) return "";
        int at = Math.max(0, Math.min(offset, body.length()));
        int start = Math.max(0, at - 38);
        int end = Math.min(body.length(), at + 38);
        int s = start;
        while (s > 0 && s > start - 8 && !Character.isWhitespace(body.charAt(s))) s--;
        int e = end;
        while (e < body.length() && e < end + 8 && !Character.isWhitespace(body.charAt(e))) e++;
        String raw = body.substring(Math.max(0, s), Math.min(body.length(), Math.max(s, e)));
        String collapsed = raw.replaceAll("\s+", " ").trim();
        if (collapsed.length() > 76) collapsed = collapsed.substring(0, 76).trim() + "\u2026";
        StringBuilder out = new StringBuilder();
        if (start > 0) out.append("\u2026");
        out.append(collapsed);
        if (end < body.length()) out.append("\u2026");
        return out.toString();
    }
}
