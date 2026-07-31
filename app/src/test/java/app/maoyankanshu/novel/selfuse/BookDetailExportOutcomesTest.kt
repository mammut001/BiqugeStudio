package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for pure [BookDetailExportOutcomes]: cancel must not surface as failure;
 * export notice matrix for soft cancel / success / hard error.
 */
class BookDetailExportOutcomesTest {

    @Test
    fun shouldSurfaceAsFailure_falseForCancellation() {
        assertFalse(BookDetailExportOutcomes.shouldSurfaceAsFailure(CancellationException("leave")))
        assertFalse(BookDetailExportOutcomes.shouldSurfaceAsFailure(CancellationException()))
    }

    @Test
    fun shouldSurfaceAsFailure_trueForOtherErrors() {
        assertTrue(BookDetailExportOutcomes.shouldSurfaceAsFailure(IllegalStateException("io")))
        assertTrue(BookDetailExportOutcomes.shouldSurfaceAsFailure(RuntimeException("boom")))
        assertTrue(BookDetailExportOutcomes.shouldSurfaceAsFailure(java.io.IOException("disk")))
    }

    @Test
    fun failMessage_appendsNonBlankDetail() {
        assertEquals(
            "导出失败",
            BookDetailExportOutcomes.failMessage("导出失败", RuntimeException()),
        )
        assertEquals(
            "导出失败",
            BookDetailExportOutcomes.failMessage("导出失败", RuntimeException("   ")),
        )
        assertEquals(
            "导出失败\n(no stream)",
            BookDetailExportOutcomes.failMessage(
                "导出失败",
                IllegalStateException("no stream"),
            ),
        )
    }

    @Test
    fun exportNotice_cancelNeverSurfaces() {
        assertEquals(
            BookDetailExportOutcomes.ExportNotice.NONE,
            BookDetailExportOutcomes.exportNotice(cancelled = true, hardError = false),
        )
        assertEquals(
            BookDetailExportOutcomes.ExportNotice.NONE,
            BookDetailExportOutcomes.exportNotice(cancelled = true, hardError = true),
        )
    }

    @Test
    fun exportNotice_successAndFail() {
        assertEquals(
            BookDetailExportOutcomes.ExportNotice.SUCCESS,
            BookDetailExportOutcomes.exportNotice(cancelled = false, hardError = false),
        )
        assertEquals(
            BookDetailExportOutcomes.ExportNotice.FAIL,
            BookDetailExportOutcomes.exportNotice(cancelled = false, hardError = true),
        )
    }
}
