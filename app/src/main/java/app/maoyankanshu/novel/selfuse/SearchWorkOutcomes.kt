package app.maoyankanshu.novel.selfuse

import kotlinx.coroutines.CancellationException

/**
 * Pure helpers for [SearchActivity] coroutine / Toast outcomes (JVM-testable).
 *
 * Local import remains `content://` / `file://` (SAF / share); Wikisource stays HTTPS-only
 * inside [WikisourceClient]. This type only classifies outcomes — it does not open network
 * or URI streams.
 */
internal object SearchWorkOutcomes {

    /**
     * [CancellationException] (user cancel, back, leave composition) must never surface as
     * search/import failure Toast or error text.
     */
    fun shouldSurfaceAsFailure(error: Throwable): Boolean = error !is CancellationException

    /** Toast / inline message choice after a multi-URI local import batch. */
    enum class LocalBatchNotice {
        /** No Toast (idle, empty batch, or cancelled mid-loop). */
        NONE,

        /** Single URI succeeded. */
        SINGLE_OK,

        /** Single URI exactly matches a book already on the shelf. */
        SINGLE_EXISTING,

        /** Multiple URIs all succeeded. */
        MULTI_OK,

        /** Multiple successful URIs include both new and existing books. */
        MULTI_WITH_EXISTING,

        /** Every successful URI was already present. */
        ALL_EXISTING,

        /** Mix of success and hard failures (not cancellation). */
        PARTIAL,

        /** Every URI hard-failed. */
        ALL_FAIL,
    }

    /**
     * Single-URI success (new or exact duplicate) opens the same detail
     * confirmation banner as HTTPS import. Multi-URI batches stay on Search
     * with a summary Toast — there is no one book to confirm.
     */
    fun opensDetailConfirmation(notice: LocalBatchNotice): Boolean =
        notice == LocalBatchNotice.SINGLE_OK || notice == LocalBatchNotice.SINGLE_EXISTING

    /** Banner [BookDetailActivity.EXTRA_JUST_ADDED]: new shelf row vs already-on-shelf. */
    fun confirmationJustAdded(notice: LocalBatchNotice): Boolean =
        notice == LocalBatchNotice.SINGLE_OK

    /**
     * @param cancelled true when the batch [kotlinx.coroutines.Job] was cancelled mid-loop —
     *   do not show success or failure Toast (already-imported books stay in the library).
     */
    fun localBatchNotice(
        added: Int,
        existing: Int,
        fail: Int,
        cancelled: Boolean,
    ): LocalBatchNotice {
        if (cancelled) return LocalBatchNotice.NONE
        val ok = added.coerceAtLeast(0) + existing.coerceAtLeast(0)
        return when {
            added == 1 && existing == 0 && fail == 0 -> LocalBatchNotice.SINGLE_OK
            added == 0 && existing == 1 && fail == 0 -> LocalBatchNotice.SINGLE_EXISTING
            added > 1 && existing == 0 && fail == 0 -> LocalBatchNotice.MULTI_OK
            added > 0 && existing > 0 && fail == 0 -> LocalBatchNotice.MULTI_WITH_EXISTING
            added == 0 && existing > 1 && fail == 0 -> LocalBatchNotice.ALL_EXISTING
            ok > 0 && fail > 0 -> LocalBatchNotice.PARTIAL
            fail > 0 -> LocalBatchNotice.ALL_FAIL
            else -> LocalBatchNotice.NONE
        }
    }

    /** Matches local import size-guard messages (`too large` / `32MB`). */
    fun isOversizedImportError(error: Throwable): Boolean {
        if (error !is IllegalArgumentException) return false
        val msg = error.message ?: return false
        return msg.contains("too large") || msg.contains("32MB")
    }
}
