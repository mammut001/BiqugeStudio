package app.maoyankanshu.novel.selfuse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadProgressThrottleTest {
    @Test
    fun fastSamplesAreCoalescedButTimedSamplePasses() {
        val throttle = DownloadProgressThrottle(minIntervalMs = 250L)

        assertTrue(throttle.shouldReport(0L, 1_000L, nowMs = 1_000L))
        assertFalse(throttle.shouldReport(100L, 1_000L, nowMs = 1_100L))
        assertTrue(throttle.shouldReport(200L, 1_000L, nowMs = 1_250L))
    }

    @Test
    fun knownOrExplicitCompletionAlwaysPassesWithoutDuplicate() {
        val known = DownloadProgressThrottle(minIntervalMs = 250L)
        assertTrue(known.shouldReport(0L, 1_000L, nowMs = 1_000L))
        assertTrue(known.shouldReport(1_000L, 1_000L, nowMs = 1_010L))
        assertFalse(known.shouldReport(1_000L, 1_000L, nowMs = 1_020L, completed = true))

        val unknown = DownloadProgressThrottle(minIntervalMs = 250L)
        assertTrue(unknown.shouldReport(0L, -1L, nowMs = 2_000L))
        assertFalse(unknown.shouldReport(900L, -1L, nowMs = 2_010L))
        assertTrue(unknown.shouldReport(900L, -1L, nowMs = 2_020L, completed = true))
    }
}
