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
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 0, fail = 0, cancelled = true),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(added = 1, existing = 1, fail = 1, cancelled = true),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 0, fail = 3, cancelled = true),
        )
    }

    @Test
    fun localBatchNotice_successAndFailureMatrix() {
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.NONE,
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 0, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK,
            SearchWorkOutcomes.localBatchNotice(added = 1, existing = 0, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.MULTI_OK,
            SearchWorkOutcomes.localBatchNotice(added = 3, existing = 0, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.SINGLE_EXISTING,
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 1, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.MULTI_WITH_EXISTING,
            SearchWorkOutcomes.localBatchNotice(added = 2, existing = 1, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.ALL_EXISTING,
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 3, fail = 0, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.PARTIAL,
            SearchWorkOutcomes.localBatchNotice(added = 1, existing = 1, fail = 1, cancelled = false),
        )
        assertEquals(
            SearchWorkOutcomes.LocalBatchNotice.ALL_FAIL,
            SearchWorkOutcomes.localBatchNotice(added = 0, existing = 0, fail = 2, cancelled = false),
        )
    }

    @Test
    fun opensDetailConfirmation_onlySingleNewOrExisting() {
        assertTrue(
            SearchWorkOutcomes.opensDetailConfirmation(
                SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK,
            ),
        )
        assertTrue(
            SearchWorkOutcomes.opensDetailConfirmation(
                SearchWorkOutcomes.LocalBatchNotice.SINGLE_EXISTING,
            ),
        )
        for (
            notice in listOf(
                SearchWorkOutcomes.LocalBatchNotice.NONE,
                SearchWorkOutcomes.LocalBatchNotice.MULTI_OK,
                SearchWorkOutcomes.LocalBatchNotice.MULTI_WITH_EXISTING,
                SearchWorkOutcomes.LocalBatchNotice.ALL_EXISTING,
                SearchWorkOutcomes.LocalBatchNotice.PARTIAL,
                SearchWorkOutcomes.LocalBatchNotice.ALL_FAIL,
            )
        ) {
            assertFalse(notice.name, SearchWorkOutcomes.opensDetailConfirmation(notice))
        }
        assertEquals("just_imported", BookDetailActivity.EXTRA_JUST_IMPORTED)
        assertEquals("just_added", BookDetailActivity.EXTRA_JUST_ADDED)
        assertTrue(
            SearchWorkOutcomes.confirmationJustAdded(
                SearchWorkOutcomes.LocalBatchNotice.SINGLE_OK,
            ),
        )
        assertFalse(
            SearchWorkOutcomes.confirmationJustAdded(
                SearchWorkOutcomes.LocalBatchNotice.SINGLE_EXISTING,
            ),
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
