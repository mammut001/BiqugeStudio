package app.maoyankanshu.novel.selfuse.ui.screens

import app.maoyankanshu.novel.selfuse.ReadingStats
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal object ReadingInsights {
    const val HEATMAP_WEEKS = 26
    const val HEATMAP_DAYS = HEATMAP_WEEKS * 7
    const val INSIGHT_LOOKBACK_DAYS = HEATMAP_DAYS + 8

    data class Summary(
        val currentWeekMillis: Long,
        val currentStreakDays: Int,
        val longestStreakDays: Int,
    )

    fun summary(entries: List<ReadingStats.DayEntry>): Summary = Summary(
        currentWeekMillis = currentWeekMillis(entries),
        currentStreakDays = currentStreakDays(entries),
        longestStreakDays = longestStreakDays(entries),
    )

    /**
     * Current streak keeps yesterday's streak alive until the user has a chance to read today.
     * Once two consecutive trailing days are empty, the current streak is zero.
     */
    fun currentStreakDays(entries: List<ReadingStats.DayEntry>): Int {
        if (entries.isEmpty()) return 0
        var index = entries.lastIndex
        if (entries[index].millis <= 0L) index--
        if (index < 0 || entries[index].millis <= 0L) return 0
        var streak = 0
        while (index >= 0 && entries[index].millis > 0L) {
            streak++
            index--
        }
        return streak
    }

    fun longestStreakDays(entries: List<ReadingStats.DayEntry>): Int {
        var best = 0
        var run = 0
        for (entry in entries) {
            if (entry.millis > 0L) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    /** Sum Monday through the latest entry's calendar day. */
    fun currentWeekMillis(entries: List<ReadingStats.DayEntry>): Long {
        if (entries.isEmpty()) return 0L
        val latest = entries.last()
        val dayOfWeek = dayOfWeek(latest.dayKey) ?: return entries.takeLast(7).sumOf { it.millis }
        val mondayBasedIndex = (dayOfWeek + 5) % 7 // Calendar.SUNDAY=1 -> 6, MONDAY=2 -> 0
        val count = (mondayBasedIndex + 1).coerceIn(1, 7)
        return entries.takeLast(count).sumOf { it.millis }
    }

    /**
     * Returns exactly 26 Monday-aligned weeks when enough continuous daily entries are supplied.
     * ReadingStats.days() always includes zero-value missing days, so the heatmap never invents data.
     */
    fun heatmapEntries(entries: List<ReadingStats.DayEntry>): List<ReadingStats.DayEntry> {
        if (entries.isEmpty()) return emptyList()
        val tail = entries.takeLast(INSIGHT_LOOKBACK_DAYS)
        val lastMondayIndex = tail.indexOfLast { dayOfWeek(it.dayKey) == Calendar.MONDAY }
        if (lastMondayIndex < 0) return tail.takeLast(HEATMAP_DAYS)

        val latestWeekStart = lastMondayIndex
        val targetStart = latestWeekStart - (HEATMAP_WEEKS - 1) * 7
        val start = targetStart.coerceAtLeast(0)
        return tail.subList(start, tail.size).takeLast(HEATMAP_DAYS)
    }

    /** Five quiet GitHub-style levels: none, <15m, <30m, <60m, 60m+. */
    fun heatLevel(millis: Long): Int = when {
        millis <= 0L -> 0
        millis < 15L * 60_000L -> 1
        millis < 30L * 60_000L -> 2
        millis < 60L * 60_000L -> 3
        else -> 4
    }

    private fun dayOfWeek(dayKey: String): Int? {
        if (dayKey.length != 8) return null
        return try {
            val formatter = SimpleDateFormat("yyyyMMdd", Locale.US).apply { isLenient = false }
            val date = formatter.parse(dayKey) ?: return null
            Calendar.getInstance().apply { time = date }.get(Calendar.DAY_OF_WEEK)
        } catch (_: ParseException) {
            null
        }
    }
}
