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
    /** TextMeasurer is deliberately avoided for very large books to prevent OOM/ANR. */
    const val MAX_EXACT_MEASURE_CHARS: Int = 200_000

    /**
     * Provisional chars/page used for large-book open before the viewport is measured.
     * Enables a readable first body on the first frame without waiting on layout.
     */
    const val DEFAULT_APPROX_CHARS_PER_PAGE: Int = 900

    /** Half-width (in pages) of a progressive page-start window around restored progress. */
    const val PROGRESSIVE_WINDOW_RADIUS: Int = 4

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
     * Safe page body for Compose: an empty / incomplete index must never feed the whole
     * [fullText] into a single page (regression guard for multi‑MB ANR/black-screen).
     */
    fun safePageText(fullText: String, pageStarts: List<Int>, pageIndex: Int): String {
        if (pageStarts.isEmpty()) return ""
        return pageText(fullText, pageStarts, pageIndex)
    }

    /**
     * Page count for approximate (virtual) pagination — O(1), no list allocation.
     * Empty book → 1 (single blank page).
     */
    fun approximatePageCount(textLength: Int, charsPerPage: Int): Int {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return 1
        val size = charsPerPage.coerceAtLeast(256)
        return ((length - 1) / size) + 1
    }

    /**
     * Character offset where virtual page [pageIndex] begins — O(1).
     */
    fun approximateOffsetForPage(pageIndex: Int, charsPerPage: Int, textLength: Int): Int {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return 0
        val size = charsPerPage.coerceAtLeast(256)
        val count = approximatePageCount(length, size)
        val page = clampPageIndex(pageIndex, count)
        return (page * size).coerceIn(0, length)
    }

    /**
     * Body substring for one approximate page — O(page size), not O(book size).
     * Never returns the entire multi‑MB string as a single page when [charsPerPage] is sane.
     */
    fun approximatePageText(fullText: String, charsPerPage: Int, pageIndex: Int): String {
        val length = fullText.length
        if (length == 0) return ""
        val size = charsPerPage.coerceAtLeast(256)
        val start = approximateOffsetForPage(pageIndex, size, length)
        val endExclusive = (start + size).coerceAtMost(length)
        if (start >= endExclusive) return ""
        return fullText.substring(start, endExclusive)
    }

    /**
     * O(1) half-open range for the page at library [progress] (0…1000).
     * Used for instant first-body materialization without building a full index.
     */
    fun firstReadablePageRange(
        textLength: Int,
        charsPerPage: Int,
        progress: Int,
    ): Pair<Int, Int> {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return 0 to 0
        val size = charsPerPage.coerceAtLeast(256)
        val count = approximatePageCount(length, size)
        val page = pageForProgress(progress, count)
        val start = (page * size).coerceIn(0, length)
        val endExclusive = (start + size).coerceAtMost(length)
        return if (start >= endExclusive) start to start else start to endExclusive
    }

    /**
     * Bounded page-start window around [progress]. Work is O(radius), not O(textLength).
     *
     * Each page stays ≤ [charsPerPage] chars. Never collapses a multi-page book into a
     * single start offset of `0` (which would make [pageText] return the whole book).
     */
    fun progressivePageStartWindow(
        textLength: Int,
        charsPerPage: Int,
        progress: Int,
        radius: Int = PROGRESSIVE_WINDOW_RADIUS,
    ): List<Int> {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return listOf(0)
        val size = charsPerPage.coerceAtLeast(256)
        val pageCount = approximatePageCount(length, size)
        val focus = pageForProgress(progress, pageCount)
        val r = radius.coerceAtLeast(0)
        val from = (focus - r).coerceAtLeast(0)
        val to = (focus + r).coerceAtMost(pageCount - 1)
        val starts = ArrayList<Int>(to - from + 1)
        for (page in from..to) {
            starts.add(page * size)
        }
        return starts
    }

    /**
     * Build bounded-size page starts without laying out the entire book.
     *
     * This is used for large imports where a full TextMeasurer layout can allocate more memory
     * than a phone can provide. Pages are intentionally approximate; each rendered page remains
     * small and the user can still read, navigate, and save progress safely.
     *
     * Prefer [approximatePageCount] + [approximatePageText] on the open hot path so first body
     * does not wait on allocating one entry per page for multi‑MB books.
     */
    fun approximatePageStartOffsets(textLength: Int, charsPerPage: Int): List<Int> {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return listOf(0)
        val size = charsPerPage.coerceAtLeast(256)
        val pageCount = approximatePageCount(length, size)
        return List(pageCount) { it * size }
    }

    /** Estimate a conservative page size for the current viewport and text style. */
    fun approximateCharsPerPage(
        widthPx: Int,
        heightPx: Int,
        fontSizePx: Float,
        lineHeightMultiplier: Float,
    ): Int {
        val font = fontSizePx.coerceAtLeast(1f)
        val lineHeight = (font * lineHeightMultiplier.coerceAtLeast(1f)).coerceAtLeast(1f)
        val columns = (widthPx.coerceAtLeast(1) / font).toInt().coerceAtLeast(1)
        val lines = (heightPx.coerceAtLeast(1) / lineHeight).toInt().coerceAtLeast(1)
        return (columns * lines * 0.82f).roundToInt().coerceIn(256, 4096)
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
