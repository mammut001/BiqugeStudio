package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for shipped [OpenProgressGate]: progressive→full swap must hold
 * [book.position] and open at [PageIndex.pageForProgress], never commit progress
 * from a stale window pager page.
 */
class OpenProgressGateTest {

    @Test
    fun mayCommit_requiresFullTextAndRestoreApplied() {
        assertFalse(OpenProgressGate.mayCommitProgressFromPageTurn(false, false))
        assertFalse(OpenProgressGate.mayCommitProgressFromPageTurn(false, true))
        assertFalse(OpenProgressGate.mayCommitProgressFromPageTurn(true, false))
        assertTrue(OpenProgressGate.mayCommitProgressFromPageTurn(true, true))
    }

    @Test
    fun progressAfterPageTurn_holdsSavedProgressUntilRestore() {
        val saved = 640
        // Stale window pager often sits at page 0 with a huge full-book pageCount.
        val clobberedIfNaive = PageIndex.progressForPage(0, 5000)
        assertEquals(0, clobberedIfNaive)

        val held = OpenProgressGate.progressAfterPageTurn(
            textFullyLoaded = true,
            restoreApplied = false,
            heldProgress = saved,
            page = 0,
            pageCount = 5000,
        )
        assertEquals(saved, held)

        // Progressive window: never commit even if restore flag is confused.
        val windowHeld = OpenProgressGate.progressAfterPageTurn(
            textFullyLoaded = false,
            restoreApplied = true,
            heldProgress = saved,
            page = 3,
            pageCount = 10,
        )
        assertEquals(saved, windowHeld)
    }

    @Test
    fun progressAfterPageTurn_commitsOnlyAfterRestore() {
        val pageCount = 11
        val page = 5
        val expected = PageIndex.progressForPage(page, pageCount)
        val got = OpenProgressGate.progressAfterPageTurn(
            textFullyLoaded = true,
            restoreApplied = true,
            heldProgress = 640,
            page = page,
            pageCount = pageCount,
        )
        assertEquals(expected, got)
    }

    @Test
    fun onFullTextSwap_progressStaysAtBookPosition_targetIsPageForProgress() {
        val saved = 750
        val fullLen = 3_000_000
        val cpp = PageIndex.DEFAULT_APPROX_CHARS_PER_PAGE
        val swap = OpenProgressGate.onFullTextSwap(saved, fullLen, cpp)

        assertEquals(saved, swap.heldProgress)
        assertFalse(swap.restoreApplied)

        val expectedPage = PageIndex.pageForProgress(saved, swap.pageCount)
        assertEquals(expectedPage, swap.targetPage)
        assertEquals(
            PageIndex.approximatePageCount(fullLen, cpp),
            swap.pageCount,
        )
        // Must not open at page 0 for mid-book progress.
        assertTrue(swap.targetPage > 0)
        assertEquals(
            expectedPage,
            OpenProgressGate.restoreTargetPage(saved, swap.pageCount),
        )
    }

    @Test
    fun onFullTextSwap_zeroProgressOpensAtPageZero() {
        val swap = OpenProgressGate.onFullTextSwap(
            savedProgress = 0,
            fullTextLength = 2_000_000,
            charsPerPage = 900,
        )
        assertEquals(0, swap.heldProgress)
        assertEquals(0, swap.targetPage)
    }

    @Test
    fun displayPageForApprox_usesProgressBeforeRestore_notStalePager() {
        val saved = 500
        val pageCount = 101
        val expected = PageIndex.pageForProgress(saved, pageCount)
        // Stale pager still on progressive window page 0.
        assertEquals(
            expected,
            OpenProgressGate.displayPageForApprox(
                restoreApplied = false,
                pagerPage = 0,
                savedProgress = saved,
                pageCount = pageCount,
            ),
        )
        // After restore, follow the pager.
        assertEquals(
            12,
            OpenProgressGate.displayPageForApprox(
                restoreApplied = true,
                pagerPage = 12,
                savedProgress = saved,
                pageCount = pageCount,
            ),
        )
    }

    @Test
    fun afterRestoreApplied_preservesHeldProgress() {
        assertEquals(640, OpenProgressGate.afterRestoreApplied(640))
        assertEquals(0, OpenProgressGate.afterRestoreApplied(-10))
        assertEquals(1000, OpenProgressGate.afterRestoreApplied(9999))
    }

    @Test
    fun swapThenPageTurn_sequenceDoesNotClobberUntilRestore() {
        val saved = 820
        val swap = OpenProgressGate.onFullTextSwap(
            savedProgress = saved,
            fullTextLength = 5_000_000,
            charsPerPage = 1000,
        )
        // Snapshot from stale page 0 before restore — must hold.
        val mid = OpenProgressGate.progressAfterPageTurn(
            textFullyLoaded = true,
            restoreApplied = swap.restoreApplied,
            heldProgress = swap.heldProgress,
            page = 0,
            pageCount = swap.pageCount,
        )
        assertEquals(saved, mid)

        // After restore applied, user turns to next page — may commit.
        val next = OpenProgressGate.progressAfterPageTurn(
            textFullyLoaded = true,
            restoreApplied = true,
            heldProgress = OpenProgressGate.afterRestoreApplied(mid),
            page = swap.targetPage + 1,
            pageCount = swap.pageCount,
        )
        assertEquals(
            PageIndex.progressForPage(swap.targetPage + 1, swap.pageCount),
            next,
        )
        assertTrue(next != 0 || swap.targetPage + 1 == 0)
    }
}
