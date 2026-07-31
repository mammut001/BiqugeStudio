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

        /** Multiple URIs all succeeded. */
        MULTI_OK,

        /** Mix of success and hard failures (not cancellation). */
        PARTIAL,

        /** Every URI hard-failed. */
        ALL_FAIL,
    }

    /**
     * @param cancelled true when the batch [kotlinx.coroutines.Job] was cancelled mid-loop —
     *   do not show success or failure Toast (already-imported books stay in the library).
     */
    fun localBatchNotice(ok: Int, fail: Int, cancelled: Boolean): LocalBatchNotice {
        if (cancelled) return LocalBatchNotice.NONE
        return when {
            ok == 1 && fail == 0 -> LocalBatchNotice.SINGLE_OK
            ok > 1 && fail == 0 -> LocalBatchNotice.MULTI_OK
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
