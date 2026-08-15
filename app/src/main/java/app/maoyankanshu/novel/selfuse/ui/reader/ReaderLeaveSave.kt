package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ReadingStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * Process-lifetime persistence used by reader leave/background paths.
 *
 * Progress and reading-time writes intentionally share one IO scope and pending-write counter so
 * the main shell can wait for both before refreshing shelf/history/statistics.
 */
object ReaderLeaveSave {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingWrites = AtomicInteger(0)

    /**
     * Elapsed reading time from monotonic elapsed-realtime samples.
     * Zero or negative delta (same tick / invalid order) becomes zero.
     */
    fun elapsedReadingMs(startedElapsedRealtime: Long, endedElapsedRealtime: Long): Long {
        val delta = endedElapsedRealtime - startedElapsedRealtime
        return if (delta <= 0L) 0L else delta
    }

    /** Matches ReadingStats guards: non-positive durations are ignored. */
    fun shouldRecordStats(durationMs: Long): Boolean = durationMs > 0L

    /** True while at least one reader persistence write is still in flight. */
    fun hasPendingWrites(): Boolean = pendingWrites.get() > 0

    /**
     * Suspend until in-flight reader writes finish, or [timeoutMs] elapses.
     * Used by the main shell so ON_RESUME refresh sees latest progress/stats.
     */
    suspend fun awaitIdle(timeoutMs: Long = 1_500L) {
        if (!hasPendingWrites()) return
        withTimeoutOrNull(timeoutMs) {
            while (hasPendingWrites()) {
                kotlinx.coroutines.delay(16L)
            }
        }
    }

    /**
     * Save reading progress off-main. [durationMs] is retained for source compatibility with the
     * existing Compose leave path, but reading time is now owned by the Activity lifecycle via
     * [recordReadingAsync] so background/locked time is never double counted.
     */
    fun persistAsync(
        context: Context,
        bookId: String,
        progress: Int,
        durationMs: Long,
    ) {
        val app = context.applicationContext
        val clamped = ProgressMath.clampProgress(progress)
        pendingWrites.incrementAndGet()
        ioScope.launch {
            try {
                LibraryStore.getForReading(app).savePosition(bookId, clamped)
            } finally {
                pendingWrites.decrementAndGet()
            }
        }
    }

    /**
     * Persist exactly one foreground-active reading segment.
     * Duration is monotonic; wall time is used only for local-day bucketing/cross-midnight split.
     */
    fun recordReadingAsync(
        context: Context,
        startedWallTimeMillis: Long,
        durationMs: Long,
    ) {
        if (!shouldRecordStats(durationMs)) return
        val app = context.applicationContext
        pendingWrites.incrementAndGet()
        ioScope.launch {
            try {
                ReadingStats.addInterval(app, startedWallTimeMillis, durationMs)
            } finally {
                pendingWrites.decrementAndGet()
            }
        }
    }
}
