package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderActiveSessionTest {

    @Test
    fun backgroundGap_isNotCountedAsReadingTime() {
        val session = ReaderActiveSession()

        session.resume(nowElapsedRealtime = 1_000L, nowWallTimeMillis = 100_000L)
        val first = session.pause(nowElapsedRealtime = 6_000L)!!

        // Twenty seconds can pass while HOME / lock screen / another app owns the foreground.
        assertNull(session.pause(nowElapsedRealtime = 26_000L))

        session.resume(nowElapsedRealtime = 26_000L, nowWallTimeMillis = 125_000L)
        val second = session.pause(nowElapsedRealtime = 28_000L)!!

        assertEquals(5_000L, first.durationMillis)
        assertEquals(2_000L, second.durationMillis)
        assertEquals(7_000L, first.durationMillis + second.durationMillis)
    }

    @Test
    fun duplicateResume_doesNotResetSegmentStart() {
        val session = ReaderActiveSession()
        session.resume(nowElapsedRealtime = 10_000L, nowWallTimeMillis = 500_000L)
        session.resume(nowElapsedRealtime = 12_000L, nowWallTimeMillis = 900_000L)

        val segment = session.pause(nowElapsedRealtime = 15_000L)!!

        assertEquals(500_000L, segment.startedWallTimeMillis)
        assertEquals(5_000L, segment.durationMillis)
    }

    @Test
    fun nonPositiveElapsedDelta_isDiscardedAndSessionResets() {
        val session = ReaderActiveSession()
        session.resume(nowElapsedRealtime = 8_000L, nowWallTimeMillis = 100L)
        assertTrue(session.isActive())

        assertNull(session.pause(nowElapsedRealtime = 7_999L))
        assertFalse(session.isActive())

        session.resume(nowElapsedRealtime = 9_000L, nowWallTimeMillis = 200L)
        assertEquals(1_000L, session.pause(nowElapsedRealtime = 10_000L)!!.durationMillis)
    }
}
