package app.maoyankanshu.novel.selfuse.ui.reader

/** Sleep timer for TTS playback. Minutes, 0 = off. Pure helpers + JVM tests. */
object TtsSleepTimer {
    const val OFF_MIN: Int = 0

    /** Off + common sleep durations. */
    val PRESETS_MIN: IntArray = intArrayOf(0, 15, 30, 60, 90)

    const val MIN_MIN: Int = 0
    const val MAX_MIN: Int = 180

    fun clampMin(minutes: Int): Int = minutes.coerceIn(MIN_MIN, MAX_MIN)

    fun isEnabled(minutes: Int): Boolean = clampMin(minutes) > 0

    fun delayMs(minutes: Int): Long {
        val m = clampMin(minutes)
        return if (m <= 0) 0L else m * 60_000L
    }

    fun label(minutes: Int): String {
        val m = clampMin(minutes)
        return if (m <= 0) "关" else "${m}分钟"
    }

    fun isPresetSelected(minutes: Int, preset: Int): Boolean =
        clampMin(minutes) == clampMin(preset)
}
