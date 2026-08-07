package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Device-only daily reading duration tracker. */
public final class ReadingStats {
    private static final String PREFS = "reading_stats";

    /** One calendar day's accumulated reading duration. */
    public static final class DayEntry {
        public final String dayKey;
        public final long millis;

        public DayEntry(String dayKey, long millis) {
            this.dayKey = dayKey;
            this.millis = millis;
        }
    }

    private static final Object LOCK = new Object();

    private ReadingStats() { }

    public static void add(Context context, long millis) {
        if (millis <= 0) return;
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            String key = dayKey();
            // commit() so concurrent leave-saves in-process cannot drop an in-flight RMW.
            prefs.edit().putLong(key, prefs.getLong(key, 0L) + millis).commit();
        }
    }

    public static long today(Context context) {
        return prefs(context).getLong(dayKey(), 0L);
    }

    public static String todayLabel(Context context) {
        return formatDuration(today(context));
    }

    /** Formats a duration for UI labels (seconds / minutes / hours). */
    public static String formatDuration(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        return minutes < 60
                ? minutes + " 分钟"
                : (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }

    public static long millisForDay(Context context, Calendar day) {
        return prefs(context).getLong(dayKey(day.getTime()), 0L);
    }

    /**
     * Returns {@code count} days ending today (inclusive), ascending by date.
     * Missing days are present with {@code millis == 0}.
     */
    public static List<DayEntry> days(Context context, int count) {
        return daysEndingAt(prefs(context), Calendar.getInstance(), count);
    }

    /** Sum of reading millis over the last {@code count} calendar days (including today). */
    public static long sumLastDays(Context context, int count) {
        return sumEntries(days(context, count));
    }

    static List<DayEntry> daysEndingAt(SharedPreferences prefs, Calendar endInclusive, int count) {
        if (count <= 0) return Collections.emptyList();
        List<DayEntry> out = new ArrayList<>(count);
        Calendar cal = (Calendar) endInclusive.clone();
        cal.add(Calendar.DAY_OF_MONTH, -(count - 1));
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        for (int i = 0; i < count; i++) {
            String key = fmt.format(cal.getTime());
            out.add(new DayEntry(key, prefs.getLong(key, 0L)));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }
        return out;
    }

    static long sumEntries(List<DayEntry> entries) {
        long sum = 0L;
        for (DayEntry entry : entries) {
            sum += entry.millis;
        }
        return sum;
    }

    static String dayKey(Date date) {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(date);
    }

    private static String dayKey() {
        return dayKey(new Date());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
