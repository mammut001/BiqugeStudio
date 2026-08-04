package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for shipped [PageLayout] helpers: page-location labels and margin → pad mapping.
 * Drives the real object under test (no reimplementation oracle).
 */
class PageLayoutTest {

    // ── Page location ──────────────────────────────────────────────────────

    @Test
    fun pageLocationLabel_firstMidLast() {
        assertEquals("1 / 120", PageLayout.pageLocationLabel(0, 120))
        assertEquals("3 / 120", PageLayout.pageLocationLabel(2, 120))
        assertEquals("120 / 120", PageLayout.pageLocationLabel(119, 120))
    }

    @Test
    fun pageLocationLabel_singleAndEmptyPageCount() {
        assertEquals("1 / 1", PageLayout.pageLocationLabel(0, 1))
        assertEquals("1 / 1", PageLayout.pageLocationLabel(0, 0))
        assertEquals("1 / 1", PageLayout.pageLocationLabel(5, 0))
        assertEquals("1 / 1", PageLayout.pageLocationLabel(-1, 1))
    }

    @Test
    fun pageLocationLabel_clampsOutOfRangeIndex() {
        assertEquals("1 / 10", PageLayout.pageLocationLabel(-3, 10))
        assertEquals("10 / 10", PageLayout.pageLocationLabel(99, 10))
    }

    @Test
    fun displayPageNumber_and_count_matchLabelParts() {
        val pageCount = 45
        for (index in listOf(0, 1, 22, 44)) {
            val label = PageLayout.pageLocationLabel(index, pageCount)
            val num = PageLayout.displayPageNumber(index, pageCount)
            val total = PageLayout.displayPageCount(pageCount)
            assertEquals("$num / $total", label)
        }
        assertEquals(1, PageLayout.displayPageCount(0))
        assertEquals(1, PageLayout.displayPageNumber(0, 0))
    }

    // ── Footer gap (body vs time/page strip) ───────────────────────────────

    @Test
    fun bodyFooterGap_isComfortableNotTight() {
        // Gap must exist and stay in a human-friendly band (not flush, not huge).
        assertTrue(PageLayout.BODY_FOOTER_GAP_DP >= 10)
        assertTrue(PageLayout.BODY_FOOTER_GAP_DP <= 24)
        assertTrue(PageLayout.FOOTER_BAR_DP >= 28)
        assertTrue(PageLayout.FOOTER_BAR_DP <= 48)
    }

    // ── Margin steps → pad dp ──────────────────────────────────────────────

    @Test
    fun clampMarginStep_boundaries() {
        assertEquals(PageLayout.MARGIN_NARROW, PageLayout.clampMarginStep(-1))
        assertEquals(PageLayout.MARGIN_NARROW, PageLayout.clampMarginStep(0))
        assertEquals(PageLayout.MARGIN_STANDARD, PageLayout.clampMarginStep(1))
        assertEquals(PageLayout.MARGIN_WIDE, PageLayout.clampMarginStep(2))
        assertEquals(PageLayout.MARGIN_WIDE, PageLayout.clampMarginStep(99))
    }

    @Test
    fun marginPads_narrowStandardWide_distinctAndOrdered() {
        val hN = PageLayout.horizontalPadDp(PageLayout.MARGIN_NARROW)
        val hS = PageLayout.horizontalPadDp(PageLayout.MARGIN_STANDARD)
        val hW = PageLayout.horizontalPadDp(PageLayout.MARGIN_WIDE)
        val vN = PageLayout.verticalPadDp(PageLayout.MARGIN_NARROW)
        val vS = PageLayout.verticalPadDp(PageLayout.MARGIN_STANDARD)
        val vW = PageLayout.verticalPadDp(PageLayout.MARGIN_WIDE)

        // At least two steps beyond a single fixed pad: three distinct values.
        assertTrue(hN < hS && hS < hW)
        assertTrue(vN < vS && vS < vW)

        // Standard matches historical fixed pad (18×16) for safe prior-install look.
        assertEquals(18, hS)
        assertEquals(16, vS)
    }

    @Test
    fun marginPads_outOfRangeUsesClampedStep() {
        assertEquals(
            PageLayout.horizontalPadDp(PageLayout.MARGIN_NARROW),
            PageLayout.horizontalPadDp(-5),
        )
        assertEquals(
            PageLayout.horizontalPadDp(PageLayout.MARGIN_WIDE),
            PageLayout.horizontalPadDp(8),
        )
        assertEquals(
            PageLayout.verticalPadDp(PageLayout.MARGIN_STANDARD),
            PageLayout.verticalPadDp(PageLayout.MARGIN_DEFAULT),
        )
    }

    @Test
    fun marginDefault_isStandard() {
        assertEquals(PageLayout.MARGIN_STANDARD, PageLayout.MARGIN_DEFAULT)
        assertEquals(
            PageLayout.horizontalPadDp(PageLayout.MARGIN_STANDARD),
            PageLayout.horizontalPadDp(PageLayout.MARGIN_DEFAULT),
        )
    }
}
