package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPageFollowTest {
    @Test
    fun approximate_onePage_hasNoCue() {
        assertTrue(
            TtsPageFollow.cuesForApproximatePages(
                textLength = 1_000,
                charsPerPage = 200,
                chunkStart = 20,
                chunkEndExclusive = 180,
                durationMs = 10_000,
            ).isEmpty(),
        )
    }

    @Test
    fun approximate_oneBoundary_mapsByCharacterRatio() {
        val cues = TtsPageFollow.cuesForApproximatePages(
            textLength = 1_000,
            charsPerPage = 200,
            chunkStart = 120,
            chunkEndExclusive = 320,
            durationMs = 10_000,
        )

        assertEquals(1, cues.size)
        assertEquals(TtsPageCue(fromPage = 0, page = 1, boundaryOffset = 200, atMillis = 4_000), cues[0])
    }

    @Test
    fun approximate_multipleBoundaries_areOrdered() {
        val cues = TtsPageFollow.cuesForApproximatePages(
            textLength = 2_000,
            charsPerPage = 200,
            chunkStart = 150,
            chunkEndExclusive = 650,
            durationMs = 20_000,
        )

        assertEquals(listOf(200, 400, 600), cues.map { it.boundaryOffset })
        assertEquals(listOf(1, 2, 3), cues.map { it.page })
        assertTrue(cues.zipWithNext().all { (a, b) -> a.atMillis < b.atMillis })
    }

    @Test
    fun boundaryExactlyAtChunkEnd_isExcluded() {
        val cues = TtsPageFollow.cuesForApproximatePages(
            textLength = 1_000,
            charsPerPage = 200,
            chunkStart = 50,
            chunkEndExclusive = 200,
            durationMs = 8_000,
        )

        assertTrue(cues.isEmpty())
    }

    @Test
    fun exactPages_useMeasuredStarts() {
        val cues = TtsPageFollow.cuesForExactPages(
            pageStarts = listOf(0, 120, 285, 460),
            textLength = 600,
            chunkStart = 80,
            chunkEndExclusive = 500,
            durationMs = 21_000,
        )

        assertEquals(listOf(120, 285, 460), cues.map { it.boundaryOffset })
        assertEquals(listOf(1, 2, 3), cues.map { it.page })
        assertEquals(listOf(0, 1, 2), cues.map { it.fromPage })
    }

    @Test
    fun invalidOrUnknownDuration_hasNoCue() {
        assertTrue(
            TtsPageFollow.cuesForApproximatePages(1_000, 200, 100, 400, 0).isEmpty(),
        )
        assertTrue(
            TtsPageFollow.cuesForExactPages(listOf(0, 200), 1_000, 100, 400, 1).isEmpty(),
        )
    }
}
