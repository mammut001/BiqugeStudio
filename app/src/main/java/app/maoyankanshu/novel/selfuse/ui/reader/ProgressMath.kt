package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.roundToInt

/** Pure 0…1000 progress helpers shared by Compose reader (and unit tests). */
object ProgressMath {
    /** LibraryStore / BookCard / reader slider scale. */
    const val PROGRESS_MIN: Int = 0
    const val PROGRESS_MAX: Int = 1000

    /** Clamp any raw progress into the shared 0…1000 store scale. */
    fun clampProgress(progress: Int): Int = progress.coerceIn(PROGRESS_MIN, PROGRESS_MAX)

    /**
     * Whole-number percent 0…100 for UI labels (footer, slider, book cards).
     * Uses the same 0…1000 library scale as [clampProgress].
     */
    fun percentOfProgress(progress: Int): Int =
        ((clampProgress(progress) / 10f).roundToInt()).coerceIn(0, 100)

    fun scrollYForProgress(progress: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return ((clampProgress(progress) / 1000f) * maxScroll).roundToInt().coerceIn(0, maxScroll)
    }

    fun progressForScrollY(scrollY: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return clampProgress(((scrollY.toFloat() / maxScroll) * 1000f).roundToInt())
    }

    /** Remote/Web import accept only https:// (case-insensitive). Local TXT/EPUB is offline. */
    fun isHttpsUrl(raw: String): Boolean = raw.trim().startsWith("https://", ignoreCase = true)
}
