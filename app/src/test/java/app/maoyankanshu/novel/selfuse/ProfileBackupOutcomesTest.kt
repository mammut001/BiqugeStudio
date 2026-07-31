package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for pure [ProfileBackupOutcomes]: cancel must not surface as failure;
 * restore empty vs success vs invalid; fail message formatting.
 */
class ProfileBackupOutcomesTest {

    @Test
    fun shouldSurfaceAsFailure_rejectsCancellation() {
        assertFalse(ProfileBackupOutcomes.shouldSurfaceAsFailure(CancellationException("leave")))
        assertFalse(ProfileBackupOutcomes.shouldSurfaceAsFailure(CancellationException()))
    }

    @Test
    fun shouldSurfaceAsFailure_acceptsHardErrors() {
        assertTrue(ProfileBackupOutcomes.shouldSurfaceAsFailure(IllegalStateException("io")))
        assertTrue(ProfileBackupOutcomes.shouldSurfaceAsFailure(RuntimeException("boom")))
        assertTrue(ProfileBackupOutcomes.shouldSurfaceAsFailure(java.io.IOException("disk")))
    }

    @Test
    fun failMessage_appendsNonBlankDetail() {
        assertEquals(
            "导出失败",
            ProfileBackupOutcomes.failMessage("导出失败", RuntimeException()),
        )
        assertEquals(
            "导出失败",
            ProfileBackupOutcomes.failMessage("导出失败", RuntimeException("   ")),
        )
        assertEquals(
            "导出失败\n(permission denied)",
            ProfileBackupOutcomes.failMessage(
                "导出失败",
                RuntimeException("permission denied"),
            ),
        )
    }

    @Test
    fun restoreNotice_cancelledNeverSurfaces() {
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.NONE,
            ProfileBackupOutcomes.restoreNotice(cancelled = true, count = 0, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.NONE,
            ProfileBackupOutcomes.restoreNotice(cancelled = true, count = 5, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.NONE,
            ProfileBackupOutcomes.restoreNotice(cancelled = true, count = 0, hardError = true),
        )
    }

    @Test
    fun restoreNotice_successEmptyInvalidMatrix() {
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.SUCCESS,
            ProfileBackupOutcomes.restoreNotice(cancelled = false, count = 3, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.EMPTY,
            ProfileBackupOutcomes.restoreNotice(cancelled = false, count = 0, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.INVALID,
            ProfileBackupOutcomes.restoreNotice(cancelled = false, count = 0, hardError = true),
        )
        // Hard error wins even if a count was partially observed.
        assertEquals(
            ProfileBackupOutcomes.RestoreNotice.INVALID,
            ProfileBackupOutcomes.restoreNotice(cancelled = false, count = 2, hardError = true),
        )
    }

    @Test
    fun backupNotice_cancelledAndMatrix() {
        assertEquals(
            ProfileBackupOutcomes.BackupNotice.NONE,
            ProfileBackupOutcomes.backupNotice(cancelled = true, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.BackupNotice.NONE,
            ProfileBackupOutcomes.backupNotice(cancelled = true, hardError = true),
        )
        assertEquals(
            ProfileBackupOutcomes.BackupNotice.SUCCESS,
            ProfileBackupOutcomes.backupNotice(cancelled = false, hardError = false),
        )
        assertEquals(
            ProfileBackupOutcomes.BackupNotice.FAIL,
            ProfileBackupOutcomes.backupNotice(cancelled = false, hardError = true),
        )
    }
}
