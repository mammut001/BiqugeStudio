package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Per-book device-local bookmarks, stored independently from reading progress. */
public final class BookmarkStore {
    private static final String PREFS = "bookmarks";
    private final android.content.SharedPreferences prefs;

    private BookmarkStore(Context context) { prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static BookmarkStore get(Context context) { return new BookmarkStore(context); }

    public List<Bookmark> list(String bookId) {
        List<Bookmark> result = new ArrayList<>();
        String raw = prefs.getString(bookId, "");
        for (String row : raw.split("\\n", -1)) {
            String[] item = row.split("\\|", 2);
            if (item.length != 2) continue;
            try { result.add(new Bookmark(Integer.parseInt(item[0]), decode(item[1]))); } catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    public void add(String bookId, int progress, String label) {
        List<Bookmark> all = list(bookId);
        all.add(0, new Bookmark(Math.max(0, Math.min(1000, progress)), label));
        while (all.size() > 30) all.remove(all.size() - 1);
        save(bookId, all);
    }
    public void remove(String bookId, int index) { List<Bookmark> all = list(bookId); if (index >= 0 && index < all.size()) { all.remove(index); save(bookId, all); } }
    private void save(String bookId, List<Bookmark> all) { StringBuilder out = new StringBuilder(); for (Bookmark b : all) out.append(b.progress).append('|').append(encode(b.label)).append('\n'); prefs.edit().putString(bookId, out.toString()).apply(); }
    private static String encode(String value) { return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP); }
    private static String decode(String value) { return new String(Base64.decode(value, Base64.NO_WRAP), StandardCharsets.UTF_8); }
    public static final class Bookmark { public final int progress; public final String label; Bookmark(int progress, String label) { this.progress = progress; this.label = label; } }
}
