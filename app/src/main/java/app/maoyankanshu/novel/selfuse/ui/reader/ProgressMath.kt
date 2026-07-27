package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.roundToInt

/** Pure 0…1000 progress helpers shared by Compose reader (and unit tests). */
object ProgressMath {
    fun scrollYForProgress(progress: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return ((progress.coerceIn(0, 1000) / 1000f) * maxScroll).roundToInt().coerceIn(0, maxScroll)
    }

    fun progressForScrollY(scrollY: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return ((scrollY.toFloat() / maxScroll) * 1000f).roundToInt().coerceIn(0, 1000)
    }

    /** Remote/Web import accept only https:// (case-insensitive). Local TXT/EPUB is offline. */
    fun isHttpsUrl(raw: String): Boolean = raw.trim().startsWith("https://", ignoreCase = true)
}
