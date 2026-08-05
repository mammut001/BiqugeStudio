package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Timed auto page-turn for paginated reading (replaces legacy continuous pixel scroll).
 * Interval 0 = off. Pure helpers for prefs UI and LaunchedEffect delays.
 */
object AutoPageTurn {
    const val OFF_SEC: Int = 0
    const val MIN_SEC: Int = 0
    const val MAX_SEC: Int = 300

    /** Off + common reading paces (seconds per page). */
    val PRESETS_SEC: IntArray = intArrayOf(0, 12, 20, 30, 45, 60)

    fun clampSec(seconds: Int): Int = seconds.coerceIn(MIN_SEC, MAX_SEC)

    fun isEnabled(seconds: Int): Boolean = clampSec(seconds) > 0

    fun delayMs(seconds: Int): Long {
        val s = clampSec(seconds)
        return if (s <= 0) 0L else s * 1000L
    }

    fun label(seconds: Int): String {
        val s = clampSec(seconds)
        return if (s <= 0) "关" else "${s}s"
    }

    fun isPresetSelected(seconds: Int, preset: Int): Boolean =
        clampSec(seconds) == clampSec(preset)
}
