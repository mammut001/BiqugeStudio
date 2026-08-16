package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for shipped [PageIndex] helpers: tap zones, page clamp/step,
 * progress↔page, offset↔page, and line-metric page breaking.
 *
 * Drives the real object under test — no reimplementation oracle.
 */
class PageIndexTest {

    // ── Tap zones ──────────────────────────────────────────────────────────

    @Test
    fun tapZone_leftCenterRight_fractions() {
        assertEquals(TapZoneAction.PREV_PAGE, PageIndex.tapZoneAction(0f))
        assertEquals(TapZoneAction.PREV_PAGE, PageIndex.tapZoneAction(0.2f))
        assertEquals(TapZoneAction.PREV_PAGE, PageIndex.tapZoneAction(0.32f))

        assertEquals(TapZoneAction.TOGGLE_CHROME, PageIndex.tapZoneAction(0.34f))
        assertEquals(TapZoneAction.TOGGLE_CHROME, PageIndex.tapZoneAction(0.5f))
        assertEquals(TapZoneAction.TOGGLE_CHROME, PageIndex.tapZoneAction(0.66f))

        assertEquals(TapZoneAction.NEXT_PAGE, PageIndex.tapZoneAction(0.67f))
        assertEquals(TapZoneAction.NEXT_PAGE, PageIndex.tapZoneAction(0.85f))
        assertEquals(TapZoneAction.NEXT_PAGE, PageIndex.tapZoneAction(1f))
    }

    @Test
    fun tapZone_clampsOutOfRange() {
        assertEquals(TapZoneAction.PREV_PAGE, PageIndex.tapZoneAction(-0.5f))
        assertEquals(TapZoneAction.NEXT_PAGE, PageIndex.tapZoneAction(1.5f))
    }

    @Test
    fun tapZone_boundariesMatchKindleThirds() {
        assertEquals(TapZoneAction.PREV_PAGE, PageIndex.tapZoneAction(PageIndex.ZONE_LEFT_END - 0.001f))
        assertEquals(TapZoneAction.TOGGLE_CHROME, PageIndex.tapZoneAction(PageIndex.ZONE_LEFT_END))
        assertEquals(TapZoneAction.TOGGLE_CHROME, PageIndex.tapZoneAction(PageIndex.ZONE_RIGHT_START))
        assertEquals(TapZoneAction.NEXT_PAGE, PageIndex.tapZoneAction(PageIndex.ZONE_RIGHT_START + 0.001f))
    }

    // ── Page index clamp / step ────────────────────────────────────────────

    @Test
    fun clampPageIndex_boundariesAndEmpty() {
        assertEquals(0, PageIndex.clampPageIndex(0, 0))
        assertEquals(0, PageIndex.clampPageIndex(5, 0))
        assertEquals(0, PageIndex.clampPageIndex(-3, 10))
        assertEquals(0, PageIndex.clampPageIndex(0, 10))
        assertEquals(9, PageIndex.clampPageIndex(9, 10))
        assertEquals(9, PageIndex.clampPageIndex(99, 10))
        assertEquals(0, PageIndex.clampPageIndex(0, 1))
        assertEquals(0, PageIndex.clampPageIndex(1, 1))
    }

    @Test
    fun shouldAnimatePageTurn_onlyAdjacentStep() {
        assertTrue(PageIndex.shouldAnimatePageTurn(0, 1))
        assertTrue(PageIndex.shouldAnimatePageTurn(12, 11))
        assertTrue(!PageIndex.shouldAnimatePageTurn(0, 0))
        assertTrue(!PageIndex.shouldAnimatePageTurn(0, 2))
        assertTrue(!PageIndex.shouldAnimatePageTurn(10, 400))
        assertTrue(!PageIndex.shouldAnimatePageTurn(400, 10))
    }

    @Test
    fun stepPage_clampsAtFirstAndLast() {
        assertEquals(0, PageIndex.stepPage(0, 5, -1))
        assertEquals(0, PageIndex.stepPage(0, 5, -10))
        assertEquals(1, PageIndex.stepPage(0, 5, 1))
        assertEquals(4, PageIndex.stepPage(3, 5, 1))
        assertEquals(4, PageIndex.stepPage(4, 5, 1))
        assertEquals(4, PageIndex.stepPage(4, 5, 10))
        assertEquals(0, PageIndex.stepPage(0, 1, 1))
        assertEquals(0, PageIndex.stepPage(0, 0, 1))
    }

    // ── Progress ↔ page ────────────────────────────────────────────────────

    @Test
    fun progressAndPage_roundTripEndpoints() {
        val pages = 11
        assertEquals(0, PageIndex.pageForProgress(0, pages))
        assertEquals(10, PageIndex.pageForProgress(1000, pages))
        assertEquals(0, PageIndex.progressForPage(0, pages))
        assertEquals(1000, PageIndex.progressForPage(10, pages))
    }

    @Test
    fun progressAndPage_singleOrEmptyPage() {
        assertEquals(0, PageIndex.pageForProgress(500, 1))
        assertEquals(0, PageIndex.pageForProgress(1000, 0))
        assertEquals(0, PageIndex.progressForPage(0, 1))
        assertEquals(0, PageIndex.progressForPage(3, 0))
    }

    @Test
    fun progressAndPage_midMapsNearHalf() {
        // 5 pages → indices 0..4; progress 500 → near page 2
        val page = PageIndex.pageForProgress(500, 5)
        assertEquals(2, page)
        assertEquals(500, PageIndex.progressForPage(2, 5))
    }

    @Test
    fun progressForPage_clampsAndUsesSharedScale() {
        assertEquals(ProgressMath.PROGRESS_MIN, PageIndex.progressForPage(-5, 10))
        assertEquals(ProgressMath.PROGRESS_MAX, PageIndex.progressForPage(99, 10))
        val mid = PageIndex.progressForPage(5, 11)
        assertTrue(mid in ProgressMath.PROGRESS_MIN..ProgressMath.PROGRESS_MAX)
        assertEquals(mid, ProgressMath.clampProgress(mid))
    }

    @Test
    fun pageForProgress_clampsProgressInput() {
        assertEquals(0, PageIndex.pageForProgress(-100, 8))
        assertEquals(7, PageIndex.pageForProgress(9999, 8))
    }

    // ── Offset ↔ page ──────────────────────────────────────────────────────

    @Test
    fun pageForOffset_selectsGreatestStartNotAfterOffset() {
        val starts = listOf(0, 100, 250, 400)
        assertEquals(0, PageIndex.pageForOffset(starts, 0))
        assertEquals(0, PageIndex.pageForOffset(starts, 99))
        assertEquals(1, PageIndex.pageForOffset(starts, 100))
        assertEquals(1, PageIndex.pageForOffset(starts, 249))
        assertEquals(2, PageIndex.pageForOffset(starts, 250))
        assertEquals(3, PageIndex.pageForOffset(starts, 400))
        assertEquals(3, PageIndex.pageForOffset(starts, 9999))
        assertEquals(0, PageIndex.pageForOffset(starts, -10))
        assertEquals(0, PageIndex.pageForOffset(emptyList(), 50))
    }

    @Test
    fun offsetForPage_and_pageText_shortBook() {
        val text = "AAAAAAAAAABBBBBBBBBBCCCCCCCCCC" // 30 chars
        val starts = listOf(0, 10, 20)
        assertEquals(0, PageIndex.offsetForPage(starts, 0))
        assertEquals(10, PageIndex.offsetForPage(starts, 1))
        assertEquals(20, PageIndex.offsetForPage(starts, 2))
        assertEquals("AAAAAAAAAA", PageIndex.pageText(text, starts, 0))
        assertEquals("BBBBBBBBBB", PageIndex.pageText(text, starts, 1))
        assertEquals("CCCCCCCCCC", PageIndex.pageText(text, starts, 2))
        // out-of-range page clamps to last
        assertEquals("CCCCCCCCCC", PageIndex.pageText(text, starts, 99))
    }

    @Test
    fun pageCharRange_emptyAndBoundaries() {
        assertEquals(0 to 0, PageIndex.pageCharRange(emptyList(), 0, 100))
        assertEquals(0 to 0, PageIndex.pageCharRange(listOf(0), 0, 0))
        assertEquals(0 to 50, PageIndex.pageCharRange(listOf(0), 0, 50))
        assertEquals(10 to 30, PageIndex.pageCharRange(listOf(0, 10, 30), 1, 100))
    }

    @Test
    fun approximatePageStartOffsets_boundsLargeTextWithoutLayout() {
        assertEquals(listOf(0), PageIndex.approximatePageStartOffsets(0, 1200))
        assertEquals(listOf(0, 1000, 2000), PageIndex.approximatePageStartOffsets(2500, 1000))
        assertEquals(listOf(0, 64, 128, 192, 256), PageIndex.approximatePageStartOffsets(257, 1))
    }

    @Test
    fun approximatePaging_acceptsRealCapacitiesBelowLegacyFloors() {
        val min = PageIndex.MIN_APPROX_CHARS_PER_PAGE
        val text = "甲".repeat(457)
        assertTrue(min < 200)
        assertEquals(8, PageIndex.approximatePageCount(text.length, min))
        assertEquals(min, PageIndex.approximateOffsetForPage(1, min, text.length))
        assertEquals(3, PageIndex.approximatePageCount(text.length, 180))
        assertEquals(180, PageIndex.approximateOffsetForPage(1, 180, text.length))
        assertEquals(8, PageIndex.approximatePageCount(text.length, 1))
    }

    @Test
    fun overflowFeedback_tightensMonotonicallyToAbsoluteMinimum() {
        val first = PageIndex.tightenApproxCharsPerPageAfterOverflow(256)
        assertTrue(first in PageIndex.MIN_APPROX_CHARS_PER_PAGE until 256)
        val second = PageIndex.tightenApproxCharsPerPageAfterOverflow(first)
        assertTrue(second < first)
        assertEquals(PageIndex.MIN_APPROX_CHARS_PER_PAGE, PageIndex.tightenApproxCharsPerPageAfterOverflow(PageIndex.MIN_APPROX_CHARS_PER_PAGE))
        assertEquals(PageIndex.MIN_APPROX_CHARS_PER_PAGE, PageIndex.tightenApproxCharsPerPageAfterOverflow(1))
    }

    @Test
    fun approximateCharsPerPage_isPositiveAndConservative() {
        val count = PageIndex.approximateCharsPerPage(1080, 2000, 54f, 1.5f)
        assertTrue(count in PageIndex.MIN_APPROX_CHARS_PER_PAGE..PageIndex.APPROX_CHARS_PER_PAGE_MAX)
        assertEquals(
            PageIndex.MIN_APPROX_CHARS_PER_PAGE,
            PageIndex.approximateCharsPerPage(1, 1, 1000f, 10f),
        )
        // Must stay below theoretical full fill so last line is not clipped.
        val font = 54f
        val lineH = font * 1.5f
        val cols = ((1080 / font) * 0.92f).toInt()
        val lines = (2000 / lineH).toInt().coerceAtLeast(1)
        val theoretical = cols * lines
        assertTrue(count <= theoretical)
        assertTrue(count <= (theoretical * PageIndex.APPROX_FILL_FACTOR).toInt() + 1)
    }

    @Test
    fun approximateCharsPerPage_fillsMostOfTypicalPhoneViewport() {
        // 1080×2000 px, 18sp@3x = 54px, default line height 1.85.
        // The old 0.64 fill × two-line reserve used ~55% of the page (half-blank).
        val count = PageIndex.approximateCharsPerPage(1080, 2000, 54f, 1.85f)
        val font = 54f
        val lineH = font * 1.85f
        val cols = ((1080 / font) * 0.92f).toInt()
        val rawLines = (2000 / lineH).toInt()
        val raw = cols * rawLines
        assertTrue(count >= (raw * 0.75f).toInt())
        assertTrue(count < raw)
    }

    @Test
    fun charsPerPageFromMeasuredLines_hardWrappedFillsViewport() {
        // 20 hard-wrapped lines × 30px; viewport 400 with 0.2-line safety → 394.
        // Lines 0–12 fit (bottom 390 ≤ 394); line 13 (420) does not.
        val n = 20
        val tops = FloatArray(n) { it * 30f }
        val bottoms = FloatArray(n) { (it + 1) * 30f }
        val chars = IntArray(n) { it * 22 }
        val fitted = PageIndex.charsPerPageFromMeasuredLines(
            tops,
            bottoms,
            chars,
            sampleLength = 440,
            viewportHeightPx = 400f,
        )
        assertEquals(13 * 22, fitted)
        assertTrue(fitted > 200)
    }

    @Test
    fun charsPerPageFromMeasuredLines_shortSampleReturnsSampleLength() {
        val tops = floatArrayOf(0f, 20f, 40f)
        val bottoms = floatArrayOf(20f, 40f, 60f)
        val chars = intArrayOf(0, 10, 20)
        assertEquals(
            80,
            PageIndex.charsPerPageFromMeasuredLines(
                tops,
                bottoms,
                chars,
                sampleLength = 80,
                viewportHeightPx = 400f,
            ),
        )
    }

    @Test
    fun paintedTextHeightPx_usesLastLineBottom() {
        assertEquals(0f, PageIndex.paintedTextHeightPx(0, 100f), 0.01f)
        assertEquals(480f, PageIndex.paintedTextHeightPx(12, 480f), 0.01f)
    }

    @Test
    fun expandAfterUnderfill_growsHalfEmptyFullSlice() {
        val grown = PageIndex.expandApproxCharsPerPageAfterUnderfill(
            charsPerPage = 300,
            paintedHeightPx = 500f,
            viewportHeightPx = 1000f,
        )
        assertTrue(grown > 300)
        assertTrue(grown <= PageIndex.APPROX_CHARS_PER_PAGE_MAX)
        assertTrue(
            PageIndex.expandApproxCharsPerPageAfterUnderfill(300, 900f, 1000f) > 300,
        )
        assertEquals(
            300,
            PageIndex.expandApproxCharsPerPageAfterUnderfill(300, 980f, 1000f),
        )
        assertEquals(
            300,
            PageIndex.expandApproxCharsPerPageAfterUnderfill(300, 50f, 1000f),
        )
        assertEquals(
            300,
            PageIndex.expandApproxCharsPerPageAfterUnderfill(300, 0f, 1000f),
        )
    }

    @Test
    fun effectivePageViewportHeight_reservesBottomSafety() {
        val effective = PageIndex.effectivePageViewportHeight(1000f, typicalLineHeightPx = 40f)
        assertTrue(effective < 1000f)
        // Thin descender pad only — a full empty line left a gap above the footer.
        assertEquals(
            1000f - (40f * PageIndex.PAGE_BOTTOM_SAFETY_LINE_FRACTION),
            effective,
            0.01f,
        )
        assertEquals(0.2f, PageIndex.PAGE_BOTTOM_SAFETY_LINE_FRACTION, 0.001f)
        val noLine = PageIndex.effectivePageViewportHeight(500f, 0f)
        assertTrue(noLine < 500f)
        assertTrue(noLine >= 500f * 0.5f)
    }

    @Test
    fun shouldApplyParagraphIndent_onlyAtParagraphStarts() {
        val text = "第一段开头\n第二段开头续写"
        assertTrue(PageIndex.shouldApplyParagraphIndent(text, 0))
        assertTrue(PageIndex.shouldApplyParagraphIndent(text, text.indexOf('第', 1))) // 第二段
        // Mid-paragraph (after first char of line 1)
        assertTrue(!PageIndex.shouldApplyParagraphIndent(text, 1))
        assertTrue(!PageIndex.shouldApplyParagraphIndent(text, 3))
        assertTrue(!PageIndex.shouldApplyParagraphIndent("", 0))
        // After newline
        val nl = text.indexOf('\n')
        assertTrue(PageIndex.shouldApplyParagraphIndent(text, nl + 1))
    }

    @Test
    fun approximatePageCount_andOffset_matchStartList() {
        val length = 2500
        val cpp = 1000
        val starts = PageIndex.approximatePageStartOffsets(length, cpp)
        assertEquals(starts.size, PageIndex.approximatePageCount(length, cpp))
        for (i in starts.indices) {
            assertEquals(starts[i], PageIndex.approximateOffsetForPage(i, cpp, length))
        }
    }

    @Test
    fun safePageText_emptyStartsIsEmpty_notWholeBook() {
        val text = "整本书正文".repeat(100)
        assertEquals("", PageIndex.safePageText(text, emptyList(), 0))
        val starts = listOf(0, 3, 6)
        assertEquals(text.substring(0, 3), PageIndex.safePageText(text, starts, 0))
    }

    // ── Line-metric page breaking ──────────────────────────────────────────

    @Test
    fun pageStartOffsets_emptyLines_returnsZero() {
        assertEquals(
            listOf(0),
            PageIndex.pageStartOffsets(
                FloatArray(0),
                FloatArray(0),
                IntArray(0),
                viewportHeightPx = 100f,
            ),
        )
    }

    @Test
    fun pageStartOffsets_shortTextFitsOnePage() {
        // 3 lines, 20px each, viewport 100 → one page
        val tops = floatArrayOf(0f, 20f, 40f)
        val bottoms = floatArrayOf(20f, 40f, 60f)
        val chars = intArrayOf(0, 10, 20)
        val starts = PageIndex.pageStartOffsets(tops, bottoms, chars, viewportHeightPx = 100f)
        assertEquals(listOf(0), starts)
    }

    @Test
    fun pageStartOffsets_multiPagePacking() {
        // 5 lines × 30px; viewport 100 with 0.2-line safety (6) → effective 94
        // line0+1+2: bottom 90 ≤ 94 → same page
        // line3: bottom 120 > 94 → new page at char 30
        // line4: 150-90=60 ≤ 94
        val tops = floatArrayOf(0f, 30f, 60f, 90f, 120f)
        val bottoms = floatArrayOf(30f, 60f, 90f, 120f, 150f)
        val chars = intArrayOf(0, 10, 20, 30, 40)
        val starts = PageIndex.pageStartOffsets(tops, bottoms, chars, viewportHeightPx = 100f)
        assertEquals(listOf(0, 30), starts)
        assertEquals(2, starts.size)
    }

    @Test
    fun pageStartOffsets_doesNotPackLineThatTouchesViewportEdge() {
        // 0.2-line safety: line height 50, viewport 100 → effective 90
        // line0: 0-50 fits; line1: 100-0=100 > 90 → new page
        val tops = floatArrayOf(0f, 50f)
        val bottoms = floatArrayOf(50f, 100f)
        val chars = intArrayOf(0, 20)
        val starts = PageIndex.pageStartOffsets(tops, bottoms, chars, viewportHeightPx = 100f)
        assertEquals(listOf(0, 20), starts)
    }

    @Test
    fun pageStartOffsets_tallLineStillOwnPage() {
        // Single line taller than viewport: still starts at 0; next line opens page 2
        val tops = floatArrayOf(0f, 200f)
        val bottoms = floatArrayOf(200f, 220f)
        val chars = intArrayOf(0, 50)
        val starts = PageIndex.pageStartOffsets(tops, bottoms, chars, viewportHeightPx = 100f)
        assertEquals(listOf(0, 50), starts)
    }

    @Test
    fun pageStartOffsets_thenProgressMapsAcrossPages() {
        // viewport 120, line height 40 → effective 112 (0.2-line safety)
        // line0+1: 80 ≤ 112; line2: 120 > 112 → page at 16; line3: 160-80=80; line4: 200-80=120 > 112 → page at 32
        val tops = floatArrayOf(0f, 40f, 80f, 120f, 160f)
        val bottoms = floatArrayOf(40f, 80f, 120f, 160f, 200f)
        val chars = intArrayOf(0, 8, 16, 24, 32)
        val starts = PageIndex.pageStartOffsets(tops, bottoms, chars, viewportHeightPx = 120f)
        assertEquals(listOf(0, 16, 32), starts)

        assertEquals(0, PageIndex.pageForProgress(0, starts.size))
        assertEquals(starts.size - 1, PageIndex.pageForProgress(1000, starts.size))
        assertEquals(0, PageIndex.progressForPage(0, starts.size))
        assertEquals(1000, PageIndex.progressForPage(starts.size - 1, starts.size))

        // offset mid-page → correct page
        assertEquals(0, PageIndex.pageForOffset(starts, 5))
        assertEquals(1, PageIndex.pageForOffset(starts, 16))
        assertEquals(2, PageIndex.pageForOffset(starts, 32))
    }
}
