package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for pure leave-save math used by Compose [ReaderScreen] onDispose.
 * Does not touch Android [android.content.Context] / [LibraryStore] / [ReadingStats].
 */
class ReaderLeaveSaveTest {

    @Test
    fun elapsedReadingMs_positiveDelta() {
        assertEquals(1_500L, ReaderLeaveSave.elapsedReadingMs(10_000L, 11_500L))
    }

    @Test
    fun elapsedReadingMs_zeroOrNegativeIsZero() {
        assertEquals(0L, ReaderLeaveSave.elapsedReadingMs(5_000L, 5_000L))
        assertEquals(0L, ReaderLeaveSave.elapsedReadingMs(8_000L, 7_999L))
    }

    @Test
    fun shouldRecordStats_onlyPositive() {
        assertTrue(ReaderLeaveSave.shouldRecordStats(1L))
        assertFalse(ReaderLeaveSave.shouldRecordStats(0L))
        assertFalse(ReaderLeaveSave.shouldRecordStats(-1L))
    }

    @Test
    fun clampProgress_0to1000() {
        assertEquals(0, ProgressMath.clampProgress(-1))
        assertEquals(0, ProgressMath.clampProgress(0))
        assertEquals(500, ProgressMath.clampProgress(500))
        assertEquals(1000, ProgressMath.clampProgress(1000))
        assertEquals(1000, ProgressMath.clampProgress(1001))
        assertEquals(ProgressMath.PROGRESS_MIN, ProgressMath.clampProgress(Int.MIN_VALUE))
        assertEquals(ProgressMath.PROGRESS_MAX, ProgressMath.clampProgress(Int.MAX_VALUE))
    }

    @Test
    fun hasPendingWrites_idleByDefault() {
        // Unit tests never call persistAsync; the process-lifetime counter starts at 0.
        assertFalse(ReaderLeaveSave.hasPendingWrites())
    }
}
