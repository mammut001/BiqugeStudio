package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.roundToInt

/** Pure 0…1000 progress helpers shared by Compose reader (and unit tests). */
object ProgressMath {
    /** LibraryStore / BookCard / reader slider scale. */
    const val PROGRESS_MIN: Int = 0
    const val PROGRESS_MAX: Int = 1000

    /** Clamp any raw progress into the shared 0…1000 store scale. */
    fun clampProgress(progress: Int): Int = progress.coerceIn(PROGRESS_MIN, PROGRESS_MAX)

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
