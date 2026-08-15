package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.ReadingStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingInsightsTest {
    private fun entry(day: String, minutes: Long) =
        ReadingStats.DayEntry(day, minutes * 60_000L)

    @Test
    fun `current streak keeps yesterday alive when today is empty`() {
        val entries = listOf(
            entry("20260811", 20),
            entry("20260812", 30),
            entry("20260813", 15),
            entry("20260814", 10),
            entry("20260815", 0),
        )
        assertEquals(4, ReadingInsights.currentStreakDays(entries))
    }

    @Test
    fun `current streak breaks after two trailing empty days`() {
        val entries = listOf(
            entry("20260811", 20),
            entry("20260812", 30),
            entry("20260813", 15),
            entry("20260814", 0),
            entry("20260815", 0),
        )
        assertEquals(0, ReadingInsights.currentStreakDays(entries))
    }

    @Test
    fun `longest streak finds best historical run`() {
        val entries = listOf(
            entry("20260808", 10),
            entry("20260809", 10),
            entry("20260810", 0),
            entry("20260811", 10),
            entry("20260812", 10),
            entry("20260813", 10),
            entry("20260814", 0),
        )
        assertEquals(3, ReadingInsights.longestStreakDays(entries))
    }

    @Test
    fun `current week sums monday through friday`() {
        val entries = listOf(
            entry("20260809", 100), // Sunday, previous week
            entry("20260810", 10),  // Monday
            entry("20260811", 20),
            entry("20260812", 30),
            entry("20260813", 40),
            entry("20260814", 50),  // Friday
        )
        assertEquals(150L * 60_000L, ReadingInsights.currentWeekMillis(entries))
    }

    @Test
    fun `heat levels use reading friendly thresholds`() {
        assertEquals(0, ReadingInsights.heatLevel(0L))
        assertEquals(1, ReadingInsights.heatLevel(14L * 60_000L))
        assertEquals(2, ReadingInsights.heatLevel(15L * 60_000L))
        assertEquals(3, ReadingInsights.heatLevel(30L * 60_000L))
        assertEquals(4, ReadingInsights.heatLevel(60L * 60_000L))
    }

    @Test
    fun `heatmap keeps at most 26 weeks`() {
        val entries = (1..190).map { index ->
            // Validity/alignment is exercised elsewhere; this confirms the hard visual bound.
            entry("invalid-$index", if (index % 3 == 0) 20 else 0)
        }
        assertTrue(ReadingInsights.heatmapEntries(entries).size <= ReadingInsights.HEATMAP_DAYS)
    }
}
