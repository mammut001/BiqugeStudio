package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for pure [SearchWorkOutcomes]: cancel must not surface as failure;
 * local multi-URI batch Toast choice; oversized local import detection.
 */
class SearchWorkOutcomesTest {

    @Test
    fun shouldSurfaceAsFailure_rejectsCancellation() {
        assertFalse(SearchWorkOutcomes.shouldSurfaceAsFailure(CancellationException("leave")))
        assertFalse(SearchWorkOutcomes.shouldSurfaceAsFailure(CancellationException()))
    }

    @Test
    fun shouldSurfaceAsFailure_acceptsHardErrors() {
        assertTrue(SearchWorkOutcomes.shouldSurfaceAsFailure(IllegalStateException("network")))
        assertTrue(SearchWorkOutcomes.shouldSurfaceAsFailure(RuntimeException("boom")))
        assertTrue(SearchWorkOutcomes.shouldSurfaceAsFailure(IllegalArgumentException("too large")))
    }

    @Test
    fun localBatchNotice_cancelledNeverToasts() {
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(ok = 0, fail = 0, cancelled = true),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(ok = 2, fail = 1, cancelled = true),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(ok = 0, fail = 3, cancelled = true),
        )
    }

    @Test
    fun localBatchNotice_successAndFailureMatrix() {
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(ok = 0, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK,
            SearchWorkOutcomes.localBatchNotice(ok = 1, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.MULTI_OK,
            SearchWorkOutcomes.localBatchNotice(ok = 3, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.PARTIAL,
            SearchWorkOutcomes.localBatchNotice(ok = 2, fail = 1, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.ALL_FAIL,
            SearchWorkOutcomes.localBatchNotice(ok = 0, fail = 2, cancelled = false),
        )
    }

    @Test
    fun isOversizedImportError_matchesLocalGuards() {
        assertTrue(
            SearchWorkOutcomes.isOversizedImportError(
                IllegalArgumentException("file too large for import"),
            ),
        )
        assertTrue(
            SearchWorkOutcomes.isOversizedImportError(
                IllegalArgumentException("exceeds 32MB limit"),
            ),
        )
        assertFalse(
            SearchWorkOutcomes.isOversizedImportError(
                IllegalArgumentException("bad format"),
            ),
        )
        assertFalse(
            SearchWorkOutcomes.isOversizedImportError(
                IllegalStateException("too large"),
            ),
        )
        assertFalse(SearchWorkOutcomes.isOversizedImportError(CancellationException()))
    }
}
