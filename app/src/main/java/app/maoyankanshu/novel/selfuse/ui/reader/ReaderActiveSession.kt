package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure foreground-reading session clock.
 *
 * Duration comes from a monotonic elapsed-realtime clock. Wall time is captured only once when a
 * foreground segment starts so ReadingStats can bucket that stable duration across local days.
 */
internal class ReaderActiveSession {
    data class Segment(
        val startedWallTimeMillis: Long,
        val durationMillis: Long,
    )

    private var startedElapsedRealtime: Long? = null
    private var startedWallTimeMillis: Long? = null

    fun resume(nowElapsedRealtime: Long, nowWallTimeMillis: Long) {
        if (startedElapsedRealtime != null) return
        startedElapsedRealtime = nowElapsedRealtime
        startedWallTimeMillis = nowWallTimeMillis
    }

    fun pause(nowElapsedRealtime: Long): Segment? {
        val startedElapsed = startedElapsedRealtime ?: return null
        val startedWall = startedWallTimeMillis ?: return null
        startedElapsedRealtime = null
        startedWallTimeMillis = null

        val duration = nowElapsedRealtime - startedElapsed
        if (duration <= 0L) return null
        return Segment(
            startedWallTimeMillis = startedWall,
            durationMillis = duration,
        )
    }

    fun isActive(): Boolean = startedElapsedRealtime != null
}
