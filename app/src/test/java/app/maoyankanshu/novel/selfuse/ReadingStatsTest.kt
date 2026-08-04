package app.maoyankanshu.novel.selfuse

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReadingStatsTest {

    private class LongPrefs : SharedPreferences {
        val map = HashMap<String, Long>()

        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(this)
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) {}

        class Editor(private val prefs: LongPrefs) : SharedPreferences.Editor {
            private val pending = HashMap<String, Long?>()
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) pending[key] = null
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                prefs.map.clear()
                pending.clear()
                return this
            }
            override fun commit(): Boolean {
                apply()
                return true
            }
            override fun apply() {
                pending.forEach { (key, value) ->
                    if (value == null) prefs.map.remove(key) else prefs.map[key] = value
                }
                pending.clear()
            }
        }
    }

    private fun calendarAt(year: Int, month0: Int, day: Int): Calendar {
        return Calendar.getInstance().apply {
            clear()
            set(year, month0, day, 12, 0, 0)
        }
    }

    @Test
    fun formatDuration_boundaries() {
        assertEquals("0 秒", ReadingStats.formatDuration(0))
        assertEquals("0 秒", ReadingStats.formatDuration(-5_000))
        assertEquals("45 秒", ReadingStats.formatDuration(45_000))
        assertEquals("1 分钟", ReadingStats.formatDuration(60_000))
        assertEquals("59 分钟", ReadingStats.formatDuration(59 * 60_000L))
        assertEquals("1 小时 0 分钟", ReadingStats.formatDuration(60 * 60_000L))
        assertEquals("2 小时 5 分钟", ReadingStats.formatDuration((2 * 60 + 5) * 60_000L))
    }

    @Test
    fun daysEndingAt_emptyCount() {
        val prefs = LongPrefs()
        val end = calendarAt(2026, Calendar.AUGUST, 4)
        assertTrue(ReadingStats.daysEndingAt(prefs, end, 0).isEmpty())
        assertTrue(ReadingStats.daysEndingAt(prefs, end, -3).isEmpty())
    }

    @Test
    fun daysEndingAt_missingDaysAreZero() {
        val prefs = LongPrefs()
        val end = calendarAt(2026, Calendar.AUGUST, 4)
        val days = ReadingStats.daysEndingAt(prefs, end, 3)
        assertEquals(3, days.size)
        assertEquals("20260802", days[0].dayKey)
        assertEquals("20260803", days[1].dayKey)
        assertEquals("20260804", days[2].dayKey)
        assertEquals(0L, days[0].millis)
        assertEquals(0L, ReadingStats.sumEntries(days))
    }

    @Test
    fun daysEndingAt_singleDay() {
        val prefs = LongPrefs()
        val end = calendarAt(2026, Calendar.AUGUST, 4)
        prefs.map["20260804"] = 90_000L
        val days = ReadingStats.daysEndingAt(prefs, end, 1)
        assertEquals(1, days.size)
        assertEquals("20260804", days[0].dayKey)
        assertEquals(90_000L, days[0].millis)
        assertEquals(90_000L, ReadingStats.sumEntries(days))
    }

    @Test
    fun daysEndingAt_sumsAcrossMultipleDays() {
        val prefs = LongPrefs()
        val end = calendarAt(2026, Calendar.AUGUST, 4)
        prefs.map["20260728"] = 99_000L // outside the 7-day window (starts 20260729)
        prefs.map["20260729"] = 10_000L
        prefs.map["20260730"] = 1_000L
        prefs.map["20260801"] = 2_000L
        prefs.map["20260804"] = 3_000L
        val days = ReadingStats.daysEndingAt(prefs, end, 7)
        assertEquals(7, days.size)
        assertEquals("20260729", days[0].dayKey)
        assertEquals("20260804", days[6].dayKey)
        assertEquals(10_000L + 1_000L + 2_000L + 3_000L, ReadingStats.sumEntries(days))
    }

    @Test
    fun dayKey_formatsLocalDate() {
        val cal = calendarAt(2026, Calendar.JANUARY, 9)
        assertEquals("20260109", ReadingStats.dayKey(cal.time))
    }
}
