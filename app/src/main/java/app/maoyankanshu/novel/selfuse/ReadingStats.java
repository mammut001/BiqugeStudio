package app.maoyankanshu.novel.selfuse;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Device-only daily reading duration tracker. */
public final class ReadingStats {
    private static final String PREFS = "reading_stats";
    private ReadingStats() { }
    public static void add(Context context, long millis) {
        if (millis <= 0) return;
        android.content.SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = dayKey(); prefs.edit().putLong(key, prefs.getLong(key, 0L) + millis).apply();
    }
    public static long today(Context context) { return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(dayKey(), 0L); }
    public static String todayLabel(Context context) {
        long seconds = today(context) / 1000;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60; return minutes < 60 ? minutes + " 分钟" : (minutes / 60) + " 小时 " + (minutes % 60) + " 分钟";
    }
    private static String dayKey() { return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()); }
}
