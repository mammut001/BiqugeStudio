package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Device-only recent-reading list, independent from the shelf order. */
public final class ReadingHistory {
    private static final String PREFS = "reading_history";
    private static final String KEY = "recent_v1";
    private final SharedPreferences prefs;
    private ReadingHistory(Context context) { prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static ReadingHistory get(Context context) { return new ReadingHistory(context); }
    public void record(String bookId) {
        List<Entry> entries = list();
        Iterator<Entry> iterator = entries.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().bookId.equals(bookId)) iterator.remove();
        }
        entries.add(0, new Entry(bookId, System.currentTimeMillis()));
        while (entries.size() > 30) entries.remove(entries.size() - 1);
        StringBuilder raw = new StringBuilder(); for (Entry entry : entries) raw.append(entry.bookId).append('|').append(entry.at).append('\n');
        prefs.edit().putString(KEY, raw.toString()).apply();
    }
    public List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        for (String row : prefs.getString(KEY, "").split("\\n", -1)) {
            String[] value = row.split("\\|", 2); if (value.length != 2) continue;
            try { entries.add(new Entry(value[0], Long.parseLong(value[1]))); } catch (NumberFormatException ignored) { }
        }
        return entries;
    }
    public void clear() { prefs.edit().remove(KEY).apply(); }
    public static final class Entry { public final String bookId; public final long at; Entry(String bookId, long at) { this.bookId = bookId; this.at = at; } }
}
