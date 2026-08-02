package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure Kindle-style reading layout helpers: page location labels and body margin steps.
 *
 * Pad values are **logical dp** (not px) so Compose can convert with [androidx.compose.ui.unit.dp].
 * Steps match [app.maoyankanshu.novel.selfuse.ReaderPreferences] margin constants.
 */
object PageLayout {
    /** Narrow body margins (more text per page). */
    const val MARGIN_NARROW: Int = 0
    /** Default — matches the historical fixed 18×16 dp pad. */
    const val MARGIN_STANDARD: Int = 1
    /** Wide body margins (airier page). */
    const val MARGIN_WIDE: Int = 2

    const val MARGIN_MIN: Int = MARGIN_NARROW
    const val MARGIN_MAX: Int = MARGIN_WIDE
    const val MARGIN_DEFAULT: Int = MARGIN_STANDARD

    /** Clamp any raw margin step into the supported 0…2 range. */
    fun clampMarginStep(step: Int): Int = step.coerceIn(MARGIN_MIN, MARGIN_MAX)

    /**
     * Horizontal body padding in dp for [marginStep].
     * Narrow 12 / standard 18 / wide 28 — standard preserves pre-margin installs.
     */
    fun horizontalPadDp(marginStep: Int): Int = when (clampMarginStep(marginStep)) {
        MARGIN_NARROW -> 12
        MARGIN_WIDE -> 28
        else -> 18
    }

    /**
     * Vertical body padding in dp for [marginStep].
     * Narrow 10 / standard 16 / wide 24.
     */
    fun verticalPadDp(marginStep: Int): Int = when (clampMarginStep(marginStep)) {
        MARGIN_NARROW -> 10
        MARGIN_WIDE -> 24
        else -> 16
    }

    /**
     * 1-based page location for the footer, e.g. `"3 / 120"`.
     *
     * Empty or non-positive [pageCount] → `"1 / 1"`.
     * [pageIndex] is 0-based and is clamped into the page set.
     */
    fun pageLocationLabel(pageIndex: Int, pageCount: Int): String {
        val count = pageCount.coerceAtLeast(1)
        val display = PageIndex.clampPageIndex(pageIndex, count) + 1
        return "$display / $count"
    }

    /**
     * 1-based current page for UI (empty book → 1).
     */
    fun displayPageNumber(pageIndex: Int, pageCount: Int): Int {
        val count = pageCount.coerceAtLeast(1)
        return PageIndex.clampPageIndex(pageIndex, count) + 1
    }

    /**
     * Total pages for UI (never below 1 so the footer never shows `0 / 0`).
     */
    fun displayPageCount(pageCount: Int): Int = pageCount.coerceAtLeast(1)
}
