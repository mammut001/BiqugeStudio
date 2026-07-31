package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException

/**
 * Pure helpers for [BookDetailActivity] single-book TXT export coroutine outcomes (JVM-testable).
 *
 * CreateDocument still uses SAF `content://` (or `file://`) streams only — this type classifies
 * outcomes and does not open URIs or change [BookDetailActivity.EXTRA_ID] (`"book_id"`).
 */
internal object BookDetailExportOutcomes {

    /**
     * [CancellationException] (user cancel, leave composition, Job.cancel) must never surface
     * as export failure Toast.
     */
    fun shouldSurfaceAsFailure(error: Throwable): Boolean = error !is CancellationException

    /**
     * Append non-blank [Throwable.localizedMessage] under a base fail string (dialog / long Toast).
     * Does not classify cancellation (callers rethrow / skip UI first).
     */
    fun failMessage(base: String, error: Throwable): String {
        val detail = error.localizedMessage?.trim().orEmpty()
        return if (detail.isEmpty()) base else "$base\n($detail)"
    }

    /** UI choice after a single-book TXT export attempt. */
    enum class ExportNotice {
        /** Soft cancel / leave: no success or fail Toast. */
        NONE,

        /** Stream written via [LibraryStore.exportBook]. */
        SUCCESS,

        /** Hard failure (IO, missing book, null stream, etc.). */
        FAIL,
    }

    /**
     * @param cancelled true when the export [kotlinx.coroutines.Job] was cancelled —
     *   never show success or fail Toast.
     * @param hardError true when a non-cancel exception escaped export.
     */
    fun exportNotice(cancelled: Boolean, hardError: Boolean): ExportNotice {
        if (cancelled) return ExportNotice.NONE
        return if (hardError) ExportNotice.FAIL else ExportNotice.SUCCESS
    }
}
