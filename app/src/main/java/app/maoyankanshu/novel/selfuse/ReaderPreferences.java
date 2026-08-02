package app.maoyankanshu.novel.selfuse;

import android.content.Context;
import android.content.SharedPreferences;

/** Device-local reader preferences. */
public final class ReaderPreferences {
    private static final String PREFS = "reader_preferences";
    private static final String FONT = "font_size";
    private static final String LINE_HEIGHT = "line_height_multiplier";
    private static final String NIGHT = "night_mode";
    private static final String THEME = "reader_theme";
    private static final String BRIGHTNESS = "reader_brightness";
    private static final String TTS_RATE = "tts_rate";
    /** Body margin step: 0 narrow / 1 standard / 2 wide. Absent key → standard. */
    private static final String MARGIN = "reader_margin";
    private static final String FONT_FAMILY = "reader_font_family";
    private static final String KEEP_SCREEN_ON = "reader_keep_screen_on";
    private static final String VOLUME_PAGE_TURN = "reader_volume_page_turn";

    // ── Themes (stable ints — never reorder existing values) ───────────────
    public static final int THEME_PAPER = 0;
    public static final int THEME_NIGHT = 1;
    public static final int THEME_EYE_CARE = 2;
    public static final int THEME_WHITE = 3;
    public static final int THEME_GREEN = 4;
    public static final int THEME_PINK = 5;
    public static final int THEME_GRAY = 6;
    public static final int THEME_PARCHMENT = 7;
    public static final int THEME_SOFT_NIGHT = 8;
    public static final int THEME_MIN = THEME_PAPER;
    public static final int THEME_MAX = THEME_SOFT_NIGHT;

    public static final int MARGIN_NARROW = 0;
    public static final int MARGIN_STANDARD = 1;
    public static final int MARGIN_WIDE = 2;

    public static final int FONT_SERIF = 0;
    public static final int FONT_SANS = 1;
    public static final int FONT_DEFAULT = 2;

    public static final float DEFAULT_LINE_HEIGHT = 1.85f;
    public static final float MIN_LINE_HEIGHT = 1.2f;
    public static final float MAX_LINE_HEIGHT = 2.6f;

    private final SharedPreferences prefs;

    public ReaderPreferences(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    private ReaderPreferences(Context context) {
        this(context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE));
    }

    public static ReaderPreferences get(Context context) {
        return new ReaderPreferences(context);
    }

    public static ReaderPreferences get(SharedPreferences prefs) {
        return new ReaderPreferences(prefs);
    }

    public int fontSize() {
        return prefs.getInt(FONT, 18);
    }

    public void setFontSize(int size) {
        prefs.edit().putInt(FONT, Math.max(14, Math.min(30, size))).apply();
    }

    public float lineHeightMultiplier() {
        float val = prefs.getFloat(LINE_HEIGHT, DEFAULT_LINE_HEIGHT);
        if (!Float.isFinite(val)) {
            return DEFAULT_LINE_HEIGHT;
        }
        return Math.max(MIN_LINE_HEIGHT, Math.min(MAX_LINE_HEIGHT, val));
    }

    public void setLineHeightMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier)) {
            prefs.edit().putFloat(LINE_HEIGHT, DEFAULT_LINE_HEIGHT).apply();
            return;
        }
        float clamped = Math.max(MIN_LINE_HEIGHT, Math.min(MAX_LINE_HEIGHT, multiplier));
        prefs.edit().putFloat(LINE_HEIGHT, clamped).apply();
    }

    public boolean nightMode() {
        return prefs.getBoolean(NIGHT, false);
    }

    public void setNightMode(boolean enabled) {
        setTheme(enabled ? THEME_NIGHT : THEME_PAPER);
    }

    public int theme() {
        if (!prefs.contains(THEME)) return nightMode() ? THEME_NIGHT : THEME_PAPER;
        int raw = prefs.getInt(THEME, THEME_PAPER);
        return Math.max(THEME_MIN, Math.min(THEME_MAX, raw));
    }

    public void setTheme(int value) {
        int theme = Math.max(THEME_MIN, Math.min(THEME_MAX, value));
        boolean isNight = theme == THEME_NIGHT || theme == THEME_SOFT_NIGHT;
        prefs.edit().putInt(THEME, theme).putBoolean(NIGHT, isNight).apply();
    }

    /** -1 uses the system default; otherwise Android expects a value in 0..1. */
    public float brightness() {
        return prefs.getFloat(BRIGHTNESS, -1f);
    }

    public void setBrightness(float value) {
        prefs.edit().putFloat(BRIGHTNESS, value < 0 ? -1f : Math.max(.08f, Math.min(1f, value))).apply();
    }

    public float ttsRate() {
        return Math.max(.5f, Math.min(2f, prefs.getFloat(TTS_RATE, 1f)));
    }

    public void setTtsRate(float rate) {
        prefs.edit().putFloat(TTS_RATE, Math.max(.5f, Math.min(2f, rate))).apply();
    }

    /**
     * Body margin step for the paginated reader.
     * Missing key → {@link #MARGIN_STANDARD} so prior installs keep the historical pad.
     */
    public int margin() {
        if (!prefs.contains(MARGIN)) return MARGIN_STANDARD;
        return Math.max(MARGIN_NARROW, Math.min(MARGIN_WIDE, prefs.getInt(MARGIN, MARGIN_STANDARD)));
    }

    public void setMargin(int value) {
        int step = Math.max(MARGIN_NARROW, Math.min(MARGIN_WIDE, value));
        prefs.edit().putInt(MARGIN, step).apply();
    }

    /** Body typeface: serif (default) / sans / system default. */
    public int fontFamily() {
        if (!prefs.contains(FONT_FAMILY)) return FONT_SERIF;
        return Math.max(FONT_SERIF, Math.min(FONT_DEFAULT, prefs.getInt(FONT_FAMILY, FONT_SERIF)));
    }

    public void setFontFamily(int value) {
        int id = Math.max(FONT_SERIF, Math.min(FONT_DEFAULT, value));
        prefs.edit().putInt(FONT_FAMILY, id).apply();
    }

    /** Keep the screen awake while the Compose reader is open. Default true. */
    public boolean keepScreenOn() {
        return prefs.getBoolean(KEEP_SCREEN_ON, true);
    }

    public void setKeepScreenOn(boolean enabled) {
        prefs.edit().putBoolean(KEEP_SCREEN_ON, enabled).apply();
    }

    /** Volume keys turn pages in the Compose reader. Default true (common CN reader UX). */
    public boolean volumePageTurn() {
        return prefs.getBoolean(VOLUME_PAGE_TURN, true);
    }

    public void setVolumePageTurn(boolean enabled) {
        prefs.edit().putBoolean(VOLUME_PAGE_TURN, enabled).apply();
    }
}
