package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-local reader preferences. */
public final class ReaderPreferences {
    private static final String PREFS = "reader_preferences";
    private static final String FONT = "font_size";
    private static final String NIGHT = "night_mode";
    private static final String THEME = "reader_theme";
    private static final String BRIGHTNESS = "reader_brightness";
    private static final String TTS_RATE = "tts_rate";
    public static final int THEME_PAPER = 0;
    public static final int THEME_NIGHT = 1;
    public static final int THEME_EYE_CARE = 2;
    private final SharedPreferences prefs;

    private ReaderPreferences(Context context) { prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public static ReaderPreferences get(Context context) { return new ReaderPreferences(context); }
    public int fontSize() { return prefs.getInt(FONT, 18); }
    public void setFontSize(int size) { prefs.edit().putInt(FONT, Math.max(14, Math.min(30, size))).apply(); }
    public boolean nightMode() { return prefs.getBoolean(NIGHT, false); }
    public void setNightMode(boolean enabled) { setTheme(enabled ? THEME_NIGHT : THEME_PAPER); }
    public int theme() {
        if (!prefs.contains(THEME)) return nightMode() ? THEME_NIGHT : THEME_PAPER;
        return Math.max(THEME_PAPER, Math.min(THEME_EYE_CARE, prefs.getInt(THEME, THEME_PAPER)));
    }
    public void setTheme(int value) {
        int theme = Math.max(THEME_PAPER, Math.min(THEME_EYE_CARE, value));
        prefs.edit().putInt(THEME, theme).putBoolean(NIGHT, theme == THEME_NIGHT).apply();
    }
    /** -1 uses the system default; otherwise Android expects a value in 0..1. */
    public float brightness() { return prefs.getFloat(BRIGHTNESS, -1f); }
    public void setBrightness(float value) { prefs.edit().putFloat(BRIGHTNESS, value < 0 ? -1f : Math.max(.08f, Math.min(1f, value))).apply(); }
    public float ttsRate() { return Math.max(.5f, Math.min(2f, prefs.getFloat(TTS_RATE, 1f))); }
    public void setTtsRate(float rate) { prefs.edit().putFloat(TTS_RATE, Math.max(.5f, Math.min(2f, rate))).apply(); }
}
