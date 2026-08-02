package app.maoyankanshu.novel.selfuse.ui.reader

import app.maoyankanshu.novel.selfuse.ReaderPreferences

/**
 * Pure reading-polish helpers: auto-night schedule, paragraph indent, effective theme.
 * Fully JVM unit-testable — no Android framework types.
 */
object ReaderReadingPolish {

    /**
     * Whether [hourOfDay] (0…23) falls in the auto-night window.
     * Overnight windows (e.g. 19→7) wrap past midnight; equal start/end → never night.
     */
    fun isNightHour(
        hourOfDay: Int,
        startHour: Int = ReaderPreferences.DEFAULT_AUTO_NIGHT_START_HOUR,
        endHour: Int = ReaderPreferences.DEFAULT_AUTO_NIGHT_END_HOUR,
    ): Boolean {
        val h = hourOfDay.coerceIn(0, 23)
        val s = startHour.coerceIn(0, 23)
        val e = endHour.coerceIn(0, 23)
        if (s == e) return false
        return if (s < e) {
            h in s until e
        } else {
            h >= s || h < e
        }
    }

    /**
     * Resolve the theme to paint when auto-night may be active.
     *
     * @param autoNightEnabled user toggle
     * @param hourOfDay current local hour 0…23
     * @param manualTheme last explicit theme when auto is off
     * @param dayTheme daytime paper while auto is on
     * @param nightTheme night paper (NIGHT / SOFT_NIGHT) while auto is on
     */
    fun resolveEffectiveTheme(
        autoNightEnabled: Boolean,
        hourOfDay: Int,
        manualTheme: Int,
        dayTheme: Int,
        nightTheme: Int,
        startHour: Int = ReaderPreferences.DEFAULT_AUTO_NIGHT_START_HOUR,
        endHour: Int = ReaderPreferences.DEFAULT_AUTO_NIGHT_END_HOUR,
    ): Int {
        if (!autoNightEnabled) {
            return clampReaderTheme(manualTheme)
        }
        return if (isNightHour(hourOfDay, startHour, endHour)) {
            if (nightTheme == ReaderPreferences.THEME_SOFT_NIGHT) {
                ReaderPreferences.THEME_SOFT_NIGHT
            } else {
                ReaderPreferences.THEME_NIGHT
            }
        } else {
            val day = clampReaderTheme(dayTheme)
            if (ReaderPreferences.isNightTheme(day)) ReaderPreferences.THEME_PAPER else day
        }
    }

    /**
     * First-line fullwidth indent (两个全角空格) for each non-empty line.
     * Applied to **page body only** so pagination offsets stay on the raw book text.
     */
    fun withParagraphFirstLineIndent(text: String, enabled: Boolean): String {
        if (!enabled || text.isEmpty()) return text
        val indent = "\u3000\u3000"
        val out = StringBuilder(text.length + 32)
        var atLineStart = true
        for (i in text.indices) {
            val ch = text[i]
            if (atLineStart) {
                if (ch == '\n' || ch == '\r') {
                    out.append(ch)
                    // stay at line start for consecutive blank lines
                    continue
                }
                // Skip already-indented lines (idempotent).
                if (ch == '\u3000') {
                    out.append(ch)
                    atLineStart = false
                    continue
                }
                if (!ch.isWhitespace()) {
                    out.append(indent)
                }
                atLineStart = false
            }
            out.append(ch)
            if (ch == '\n') {
                atLineStart = true
            }
        }
        return out.toString()
    }

    /**
     * Page-turn duration in ms: 280 when animation is on, 0 for instant snap.
     */
    fun pageTurnDurationMs(animationEnabled: Boolean): Int =
        if (animationEnabled) 280 else 0
}
