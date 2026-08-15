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
import java.util.TimeZone;

/** Device-only daily reading duration tracker. */
public final class ReadingStats {
    private static final String PREFS = "reading_stats";
    private static final String KEY_WEEKLY_GOAL_MILLIS = "_weekly_goal_millis";
    public static final long DEFAULT_WEEKLY_GOAL_MILLIS = 3L * 60L * 60L * 1000L;

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

    /** Adds a duration to the current local calendar day. Kept for legacy callers. */
    public static void add(Context context, long millis) {
        if (millis <= 0) return;
        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            String key = dayKey();
            // commit() so concurrent in-process RMW writes cannot drop an update.
            prefs.edit().putLong(key, prefs.getLong(key, 0L) + millis).commit();
        }
    }

    /**
     * Adds one active reading interval and splits it at local-midnight boundaries.
     *
     * The interval length should come from a monotonic clock (elapsedRealtime), while
     * {@code startedWallTimeMillis} is only used to decide which local calendar day owns each
     * slice. This keeps duration stable even if wall-clock time changes while reading.
     */
    public static void addInterval(Context context, long startedWallTimeMillis, long durationMillis) {
        if (durationMillis <= 0L) return;
        List<DayEntry> slices = splitInterval(
                startedWallTimeMillis,
                durationMillis,
                TimeZone.getDefault());
        if (slices.isEmpty()) return;

        synchronized (LOCK) {
            SharedPreferences prefs = prefs(context);
            SharedPreferences.Editor editor = prefs.edit();
            for (DayEntry slice : slices) {
                editor.putLong(slice.dayKey, prefs.getLong(slice.dayKey, 0L) + slice.millis);
            }
            editor.commit();
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

    /** Device-only weekly goal. Stored beside the daily buckets under a reserved non-date key. */
    public static long weeklyGoalMillis(Context context) {
        long stored = prefs(context).getLong(KEY_WEEKLY_GOAL_MILLIS, DEFAULT_WEEKLY_GOAL_MILLIS);
        return stored > 0L ? stored : DEFAULT_WEEKLY_GOAL_MILLIS;
    }

    public static void setWeeklyGoalMillis(Context context, long millis) {
        if (millis <= 0L) return;
        prefs(context).edit().putLong(KEY_WEEKLY_GOAL_MILLIS, millis).apply();
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

    /** Pure interval bucketing used by production and JVM tests. */
    static List<DayEntry> splitInterval(
            long startedWallTimeMillis,
            long durationMillis,
            TimeZone timeZone) {
        if (durationMillis <= 0L) return Collections.emptyList();
        TimeZone zone = timeZone == null ? TimeZone.getDefault() : timeZone;
        long endExclusive = durationMillis > Long.MAX_VALUE - startedWallTimeMillis
                ? Long.MAX_VALUE
                : startedWallTimeMillis + durationMillis;
        if (endExclusive <= startedWallTimeMillis) return Collections.emptyList();

        List<DayEntry> out = new ArrayList<>();
        long cursor = startedWallTimeMillis;
        while (cursor < endExclusive) {
            Calendar nextMidnight = Calendar.getInstance(zone, Locale.US);
            nextMidnight.setTimeInMillis(cursor);
            nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
            nextMidnight.set(Calendar.MINUTE, 0);
            nextMidnight.set(Calendar.SECOND, 0);
            nextMidnight.set(Calendar.MILLISECOND, 0);
            nextMidnight.add(Calendar.DAY_OF_MONTH, 1);

            long boundary = nextMidnight.getTimeInMillis();
            long sliceEnd = boundary > cursor
                    ? Math.min(endExclusive, boundary)
                    : endExclusive;
            long sliceMillis = sliceEnd - cursor;
            if (sliceMillis <= 0L) break;
            out.add(new DayEntry(dayKey(new Date(cursor), zone), sliceMillis));
            cursor = sliceEnd;
        }
        return out;
    }

    static String dayKey(Date date) {
        return dayKey(date, TimeZone.getDefault());
    }

    static String dayKey(Date date, TimeZone timeZone) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd", Locale.US);
        fmt.setTimeZone(timeZone == null ? TimeZone.getDefault() : timeZone);
        return fmt.format(date);
    }

    private static String dayKey() {
        return dayKey(new Date());
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
