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

    /**
     * Absolute lower bound for all virtual-pagination math.
     *
     * Large fonts and narrow viewports can legitimately fit fewer than the old 200/256 floors.
     * Virtual paging is O(1), so correctness matters more than forcing an arbitrary page size.
     * Keep this low enough for large-text accessibility layouts while bounding pathological inputs.
     */
    const val MIN_APPROX_CHARS_PER_PAGE: Int = 64

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
     * Animate only a single-page step (tap / volume / auto-turn).
     * Slider, TOC, and bookmark jumps skip the pager animation — animating across
     * tens or hundreds of pages flashes every in-between page (3D turn + remount).
     */
    fun shouldAnimatePageTurn(fromPage: Int, toPage: Int): Boolean =
        kotlin.math.abs(toPage - fromPage) == 1

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
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
        return ((length - 1) / size) + 1
    }

    /**
     * Character offset where virtual page [pageIndex] begins — O(1).
     */
    fun approximateOffsetForPage(pageIndex: Int, charsPerPage: Int, textLength: Int): Int {
        val length = textLength.coerceAtLeast(0)
        if (length == 0) return 0
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
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
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
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
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
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
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
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
        val size = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
        val pageCount = approximatePageCount(length, size)
        return List(pageCount) { it * size }
    }

    /**
     * Fraction of theoretical columns×lines kept for approximate paging.
     * Below 1.0 so Chinese full-width + letterSpacing rarely overfill the screen
     * (overfill clips the last line in a non-scrolling page Text).
     *
     * 0.64 left half the page empty. Seed close to a full page; overflow / underfill
     * feedback corrects residual wrap error so the last line sits above the footer.
     */
    const val APPROX_FILL_FACTOR: Float = 0.97f

    /** One overflow feedback step removes roughly one typical CJK line worth of capacity. */
    const val APPROX_OVERFLOW_SHRINK_FACTOR: Float = 0.94f

    /**
     * Grow toward this fraction of the painted viewport when a full-capacity
     * slice left the lower half of the page empty (hard-wrapped TXT, tall screens).
     */
    const val APPROX_UNDERFILL_TARGET: Float = 0.99f

    /** Do not grow when the painted page already uses at least this much height. */
    const val APPROX_UNDERFILL_TRIGGER: Float = 0.96f

    /** Hard-wrapped or last-page slices below this ratio are left alone. */
    const val APPROX_UNDERFILL_MIN_USED: Float = 0.12f

    const val APPROX_CHARS_PER_PAGE_MAX: Int = 3600

    /**
     * Sample length measured with [androidx.compose.ui.text.TextMeasurer] to learn how
     * many characters actually fit on one page. Cheap (4 KiB) and respects hard wraps.
     */
    const val APPROX_MEASURE_SAMPLE_CHARS: Int = 4096

    /** Tighten capacity only after the real Compose page reports vertical overflow. */
    fun tightenApproxCharsPerPageAfterOverflow(charsPerPage: Int): Int {
        val current = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
        if (current <= MIN_APPROX_CHARS_PER_PAGE) return MIN_APPROX_CHARS_PER_PAGE
        val tightened = (current * APPROX_OVERFLOW_SHRINK_FACTOR).toInt()
        return tightened.coerceIn(MIN_APPROX_CHARS_PER_PAGE, current - 1)
    }

    /**
     * Expand capacity when a full-size approximate page painted well below the
     * viewport. Returns the current value when the ratio is already healthy,
     * unknown, or too empty to treat as a pagination miss (last page / sparse).
     */
    fun expandApproxCharsPerPageAfterUnderfill(
        charsPerPage: Int,
        paintedHeightPx: Float,
        viewportHeightPx: Float,
    ): Int {
        val current = charsPerPage.coerceAtLeast(MIN_APPROX_CHARS_PER_PAGE)
        if (paintedHeightPx <= 0f || viewportHeightPx <= 0f) return current
        val used = paintedHeightPx / viewportHeightPx
        if (used >= APPROX_UNDERFILL_TRIGGER || used < APPROX_UNDERFILL_MIN_USED) {
            return current
        }
        val expanded = (current / used * APPROX_UNDERFILL_TARGET).toInt()
        return expanded.coerceIn(current + 1, APPROX_CHARS_PER_PAGE_MAX)
    }

    /**
     * Reserve this many line-heights at the bottom of the exact-measure viewport.
     * A thin descender / subpixel pad only — a full empty line left a visible gap
     * above the footer. Clip + overflow feedback catch the rare last-glyph cut.
     */
    const val PAGE_BOTTOM_SAFETY_LINE_FRACTION: Float = 0.2f

    /** Minimum absolute bottom safety in px when line height is unknown. */
    const val PAGE_BOTTOM_SAFETY_MIN_PX: Float = 6f

    /**
     * Whether a page whose body starts at [pageStartOffset] should apply paragraph
     * first-line indent.
     *
     * Full-book measure indents only true paragraph starts. Re-laying each page as
     * its own [androidx.compose.ui.text.Text] would re-indent mid-paragraph lines,
     * wrap more, and clip the last line — the recurring “last line half-visible” bug.
     */
    fun shouldApplyParagraphIndent(fullText: String, pageStartOffset: Int): Boolean {
        if (fullText.isEmpty()) return false
        val o = pageStartOffset.coerceIn(0, fullText.length)
        if (o <= 0) return true
        // Paragraph start: previous char is a line break (and skip trailing CR).
        var i = o - 1
        if (i >= 0 && fullText[i] == '\r') i--
        return i < 0 || fullText[i] == '\n'
    }

    /**
     * Effective height used when packing lines onto a page.
     * Smaller than the painted viewport so the last line fully displays.
     *
     * @param viewportHeightPx measured body height (already after margins)
     * @param typicalLineHeightPx optional average line height; when >0 reserves a
     *   thin descender pad (see [PAGE_BOTTOM_SAFETY_LINE_FRACTION]), otherwise a
     *   small absolute inset.
     */
    fun effectivePageViewportHeight(
        viewportHeightPx: Float,
        typicalLineHeightPx: Float = 0f,
    ): Float {
        val vh = viewportHeightPx.coerceAtLeast(1f)
        val reserve = if (typicalLineHeightPx > 0f) {
            (typicalLineHeightPx * PAGE_BOTTOM_SAFETY_LINE_FRACTION)
                .coerceAtLeast(PAGE_BOTTOM_SAFETY_MIN_PX)
        } else {
            PAGE_BOTTOM_SAFETY_MIN_PX
        }
        // Never reserve more than ~40% of the viewport (tiny screens / huge fonts).
        val capped = reserve.coerceAtMost(vh * 0.4f)
        return (vh - capped).coerceAtLeast(1f)
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
        // Full-width CJK ≈ 1em; letterSpacing makes real columns slightly fewer.
        val columns = ((widthPx.coerceAtLeast(1) / font) * 0.92f).toInt().coerceAtLeast(1)
        val rawLines = (heightPx.coerceAtLeast(1) / lineHeight).toInt().coerceAtLeast(1)
        return (columns * rawLines * APPROX_FILL_FACTOR).roundToInt()
            .coerceIn(MIN_APPROX_CHARS_PER_PAGE, APPROX_CHARS_PER_PAGE_MAX)
    }

    /**
     * Characters that fit on the first page of a **measured** sample.
     *
     * Uses the same line-packing rule as [pageStartOffsets] so hard-wrapped TXT
     * (one visual line ≈ 20 CJK + newline) is not treated as a full-width grid.
     * If the whole sample still fits, returns [sampleLength] so the caller can
     * enlarge the sample or keep the seed.
     */
    fun charsPerPageFromMeasuredLines(
        lineTops: FloatArray,
        lineBottoms: FloatArray,
        lineCharStarts: IntArray,
        sampleLength: Int,
        viewportHeightPx: Float,
    ): Int {
        val n = lineTops.size
        require(lineBottoms.size == n && lineCharStarts.size == n) {
            "line metric arrays must have equal length"
        }
        val sample = sampleLength.coerceAtLeast(0)
        if (n == 0 || sample == 0) return MIN_APPROX_CHARS_PER_PAGE
        val typical = (lineBottoms[0] - lineTops[0]).coerceAtLeast(0f)
        val vh = effectivePageViewportHeight(viewportHeightPx, typical)
        val pageTop = lineTops[0]
        val origin = lineCharStarts[0].coerceAtLeast(0)
        for (i in 1 until n) {
            if (lineBottoms[i] - pageTop > vh) {
                val chars = (lineCharStarts[i] - origin).coerceAtLeast(1)
                return chars.coerceIn(MIN_APPROX_CHARS_PER_PAGE, APPROX_CHARS_PER_PAGE_MAX)
            }
        }
        return sample.coerceIn(MIN_APPROX_CHARS_PER_PAGE, APPROX_CHARS_PER_PAGE_MAX)
    }

    /** Ink height of a laid-out page (last line bottom), not the constrained box size. */
    fun paintedTextHeightPx(lineCount: Int, lastLineBottomPx: Float): Float {
        if (lineCount <= 0) return 0f
        return lastLineBottomPx.coerceAtLeast(0f)
    }

    /**
     * Build page-start **character offsets** from measured line metrics.
     *
     * Packs consecutive lines into pages whose total height does not exceed
     * [viewportHeightPx] (after [effectivePageViewportHeight] safety). A line taller
     * than the viewport still occupies its own page (no mid-line split). Always returns
     * at least `[0]` when there are no lines (empty body) so the reader can show a blank page.
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

        val typicalLine = if (n > 0) {
            (lineBottoms[0] - lineTops[0]).coerceAtLeast(0f)
        } else {
            0f
        }
        // Safety margin so the last line is fully visible (not clipped by page edge).
        val vh = effectivePageViewportHeight(viewportHeightPx, typicalLine)
        val starts = ArrayList<Int>(n.coerceAtMost(64))
        starts.add(lineCharStarts[0].coerceAtLeast(0))
        var pageTop = lineTops[0]

        for (i in 1 until n) {
            val bottom = lineBottoms[i]
            // Line i does not fit below the current page top → open a new page.
            // Use strict ≤ vh (no +0.5 slack that used to pack one line too many).
            if (bottom - pageTop > vh) {
                starts.add(lineCharStarts[i].coerceAtLeast(0))
                pageTop = lineTops[i]
            }
        }
        return starts
    }
}
