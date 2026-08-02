package app.maoyankanshu.novel.selfuse.ui.reader

import android.content.Context
import app.maoyankanshu.novel.selfuse.LibraryStore
import app.maoyankanshu.novel.selfuse.ReadingStats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Leave-path persistence for Compose [ReaderScreen].
 *
 * Pure helpers ([elapsedReadingMs], [shouldRecordStats]) are JVM unit-testable
 * with no Android runtime. [persistAsync] uses a **process-lifetime** IO scope so
 * [androidx.compose.runtime.DisposableEffect] `onDispose` does not allocate a
 * fire-and-forget [CoroutineScope] per leave (unstructured concurrency anti-pattern
 * on API 23+ / Compose).
 *
 * Progress is always written through [ProgressMath.clampProgress] (0…1000).
 */
object ReaderLeaveSave {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Elapsed reading time from [android.os.SystemClock.elapsedRealtime] samples.
     * Zero or negative delta (clock skew / same-tick leave) → `0`.
     */
    fun elapsedReadingMs(startedElapsedRealtime: Long, endedElapsedRealtime: Long): Long {
        val delta = endedElapsedRealtime - startedElapsedRealtime
        return if (delta <= 0L) 0L else delta
    }

    /** Matches [ReadingStats.add] guard: non-positive durations are ignored. */
    fun shouldRecordStats(durationMs: Long): Boolean = durationMs > 0L

    /**
     * Schedule daily stats + position write off the main thread.
     * Safe to call from Compose `onDispose` (does not use [rememberCoroutineScope]).
     */
    fun persistAsync(
        context: Context,
        bookId: String,
        progress: Int,
        durationMs: Long,
    ) {
        val app = context.applicationContext
        val clamped = ProgressMath.clampProgress(progress)
        val duration = if (shouldRecordStats(durationMs)) durationMs else 0L
        ioScope.launch {
            if (duration > 0L) {
                ReadingStats.add(app, duration)
            }
            LibraryStore.getForReading(app).savePosition(bookId, clamped)
        }
    }
}
