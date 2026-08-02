package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.roundToInt

/** Kindle-style tap region → reading action. */
enum class TapZoneAction {
    PREV_PAGE,
    TOGGLE_CHROME,
    NEXT_PAGE,
}

/**
 * Pure pagination / tap-zone / progress↔page helpers for the Compose reader.
 *
 * Layout measurement stays in Compose ([androidx.compose.ui.text.TextLayoutResult]);
 * these functions only operate on arrays, indices, and the shared 0…1000 progress scale
 * so they are fully JVM unit-testable without a device.
 */
object PageIndex {
    /** Left third → previous page; center third → chrome; right third → next page. */
    const val ZONE_LEFT_END: Float = 1f / 3f
    const val ZONE_RIGHT_START: Float = 2f / 3f

    /**
     * Classify a horizontal tap on the reading surface.
     *
     * @param xFraction horizontal position as a fraction of surface width (0 = left edge, 1 = right).
     * Values outside 0…1 are clamped.
     */
    fun tapZoneAction(xFraction: Float): TapZoneAction {
        val x = xFraction.coerceIn(0f, 1f)
        return when {
            x < ZONE_LEFT_END -> TapZoneAction.PREV_PAGE
            x > ZONE_RIGHT_START -> TapZoneAction.NEXT_PAGE
            else -> TapZoneAction.TOGGLE_CHROME
        }
    }

    /** Clamp [page] into `0 until pageCount` (empty / non-positive count → 0). */
    fun clampPageIndex(page: Int, pageCount: Int): Int {
        if (pageCount <= 0) return 0
        return page.coerceIn(0, pageCount - 1)
    }

    /**
     * Step [current] by [delta] pages and clamp at first/last.
     * Does not throw when already at an edge.
     */
    fun stepPage(current: Int, pageCount: Int, delta: Int): Int =
        clampPageIndex(current + delta, pageCount)

    /**
     * Map library progress (0…1000) onto a page index.
     * Single-page (or empty) books always resolve to page 0.
     */
    fun pageForProgress(progress: Int, pageCount: Int): Int {
        if (pageCount <= 1) return 0
        val p = ProgressMath.clampProgress(progress)
        return clampPageIndex(
            ((p / 1000f) * (pageCount - 1)).roundToInt(),
            pageCount,
        )
    }

    /**
     * Map page index onto library progress (0…1000).
     * Last page → 1000; first / sole page → 0.
     */
    fun progressForPage(page: Int, pageCount: Int): Int {
        if (pageCount <= 1) return ProgressMath.PROGRESS_MIN
        val p = clampPageIndex(page, pageCount)
        return ProgressMath.clampProgress(
            ((p.toFloat() / (pageCount - 1).toFloat()) * 1000f).roundToInt(),
        )
    }

    /**
     * Index of the page whose start offset is the greatest value ≤ [offset].
     * Empty [pageStarts] → 0; negative [offset] treated as 0.
     * [pageStarts] must be sorted ascending (as produced by [pageStartOffsets]).
     */
    fun pageForOffset(pageStarts: List<Int>, offset: Int): Int {
        if (pageStarts.isEmpty()) return 0
        val o = if (offset < 0) 0 else offset
        var selected = 0
        for (i in 1 until pageStarts.size) {
            if (pageStarts[i] > o) break
            selected = i
        }
        return selected
    }

    /**
     * Character offset where [pageIndex] begins, or 0 when unknown.
     */
    fun offsetForPage(pageStarts: List<Int>, pageIndex: Int): Int {
        if (pageStarts.isEmpty()) return 0
        val i = clampPageIndex(pageIndex, pageStarts.size)
        return pageStarts[i].coerceAtLeast(0)
    }

    /**
     * Half-open character range `[start, endExclusive)` covering one page of [textLength].
     * Empty page / empty book → `(0, 0)`.
     */
    fun pageCharRange(pageStarts: List<Int>, pageIndex: Int, textLength: Int): Pair<Int, Int> {
        val len = textLength.coerceAtLeast(0)
        if (pageStarts.isEmpty() || len == 0) return 0 to 0
        val i = clampPageIndex(pageIndex, pageStarts.size)
        val start = pageStarts[i].coerceIn(0, len)
        val endExclusive = if (i + 1 < pageStarts.size) {
            pageStarts[i + 1].coerceIn(0, len)
        } else {
            len
        }
        return if (start >= endExclusive) start to start else start to endExclusive
    }

    /** Substring for [pageIndex], or empty when out of range / empty book. */
    fun pageText(fullText: String, pageStarts: List<Int>, pageIndex: Int): String {
        val (start, endExclusive) = pageCharRange(pageStarts, pageIndex, fullText.length)
        if (start >= endExclusive) return ""
        return fullText.substring(start, endExclusive)
    }

    /**
     * Build page-start **character offsets** from measured line metrics.
     *
     * Packs consecutive lines into pages whose total height does not exceed
     * [viewportHeightPx]. A line taller than the viewport still occupies its own page
     * (no mid-line split). Always returns at least `[0]` when there are no lines
     * (empty body) so the reader can show a blank page.
     *
     * @param lineTops top Y of each line in layout coordinates
     * @param lineBottoms bottom Y of each line
     * @param lineCharStarts character offset at the start of each line
     * @param viewportHeightPx available height for body text on one screen
     */
    fun pageStartOffsets(
        lineTops: FloatArray,
        lineBottoms: FloatArray,
        lineCharStarts: IntArray,
        viewportHeightPx: Float,
    ): List<Int> {
        val n = lineTops.size
        require(lineBottoms.size == n && lineCharStarts.size == n) {
            "line metric arrays must have equal length"
        }
        if (n == 0) return listOf(0)

        val vh = viewportHeightPx.coerceAtLeast(1f)
        val starts = ArrayList<Int>(n.coerceAtMost(64))
        starts.add(lineCharStarts[0].coerceAtLeast(0))
        var pageTop = lineTops[0]

        for (i in 1 until n) {
            val bottom = lineBottoms[i]
            // Line i does not fit below the current page top → open a new page.
            if (bottom - pageTop > vh + 0.5f) {
                starts.add(lineCharStarts[i].coerceAtLeast(0))
                pageTop = lineTops[i]
            }
        }
        return starts
    }
}
