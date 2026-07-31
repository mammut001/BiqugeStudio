package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException

/**
 * Pure helpers for [app.maoyankanshu.novel.selfuse.ui.screens.ProfileScreen] backup / restore
 * coroutine outcomes (JVM-testable).
 *
 * CreateDocument / OpenDocument still use SAF `content://` (or `file://`) streams only —
 * this type classifies outcomes and does not open URIs or network.
 */
internal object ProfileBackupOutcomes {

    /**
     * [CancellationException] (user cancel, leave composition, Job.cancel) must never surface
     * as backup/restore failure Toast or error dialog.
     */
    fun shouldSurfaceAsFailure(error: Throwable): Boolean = error !is CancellationException

    /**
     * Append non-blank [Throwable.localizedMessage] under a base fail string for dialog text.
     * Does not classify cancellation (callers rethrow / skip UI first).
     */
    fun failMessage(base: String, error: Throwable): String {
        val detail = error.localizedMessage?.trim().orEmpty()
        return if (detail.isEmpty()) base else "$base\n($detail)"
    }

    /** UI choice after a restore attempt (not used when host cannot accept UI). */
    enum class RestoreNotice {
        /** Soft cancel / leave: no Toast and no error dialog. */
        NONE,

        /** [count] books imported. */
        SUCCESS,

        /** Stream opened but zero books. */
        EMPTY,

        /** Hard failure (invalid zip, IO, etc.). */
        INVALID,
    }

    /**
     * @param cancelled true when the restore [kotlinx.coroutines.Job] was cancelled —
     *   never show success Toast or fail dialog.
     * @param count books restored when the import completed without hard error.
     * @param hardError true when a non-cancel exception escaped import.
     */
    fun restoreNotice(cancelled: Boolean, count: Int, hardError: Boolean): RestoreNotice {
        if (cancelled) return RestoreNotice.NONE
        if (hardError) return RestoreNotice.INVALID
        return if (count > 0) RestoreNotice.SUCCESS else RestoreNotice.EMPTY
    }

    /** UI choice after a backup export attempt. */
    enum class BackupNotice {
        NONE,
        SUCCESS,
        FAIL,
    }

    fun backupNotice(cancelled: Boolean, hardError: Boolean): BackupNotice {
        if (cancelled) return BackupNotice.NONE
        return if (hardError) BackupNotice.FAIL else BackupNotice.SUCCESS
    }
}
