package app.maoyankanshu.novel.selfuse

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderPreferencesTest {

    private class TestSharedPreferences : SharedPreferences {
        private val intMap = HashMap<String, Int>()
        private val floatMap = HashMap<String, Float>()
        private val boolMap = HashMap<String, Boolean>()

        override fun getAll(): MutableMap<String, *> = HashMap<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = null
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = intMap[key] ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = floatMap[key] ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = boolMap[key] ?: defValue
        override fun contains(key: String?): Boolean =
            intMap.containsKey(key) || floatMap.containsKey(key) || boolMap.containsKey(key)

        override fun edit(): SharedPreferences.Editor = TestEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val prefs: TestSharedPreferences) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) prefs.intMap[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) prefs.floatMap[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) prefs.boolMap[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                key?.let {
                    prefs.intMap.remove(it)
                    prefs.floatMap.remove(it)
                    prefs.boolMap.remove(it)
                }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                prefs.intMap.clear()
                prefs.floatMap.clear()
                prefs.boolMap.clear()
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    @Test
    fun fontSize_defaultAndClamping() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(18, readerPrefs.fontSize())

        readerPrefs.setFontSize(10)
        assertEquals(14, readerPrefs.fontSize())

        readerPrefs.setFontSize(40)
        assertEquals(30, readerPrefs.fontSize())

        readerPrefs.setFontSize(22)
        assertEquals(22, readerPrefs.fontSize())
    }

    @Test
    fun lineHeightMultiplier_defaultAndClamping() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        readerPrefs.setLineHeightMultiplier(0.5f)
        assertEquals(ReaderPreferences.MIN_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        readerPrefs.setLineHeightMultiplier(3.5f)
        assertEquals(ReaderPreferences.MAX_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        readerPrefs.setLineHeightMultiplier(2.1f)
        assertEquals(2.1f, readerPrefs.lineHeightMultiplier(), 0.01f)
    }

    @Test
    fun lineHeightMultiplier_handlesNaNAndInfinity() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        readerPrefs.setLineHeightMultiplier(Float.NaN)
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        readerPrefs.setLineHeightMultiplier(Float.POSITIVE_INFINITY)
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        readerPrefs.setLineHeightMultiplier(Float.NEGATIVE_INFINITY)
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        prefs.edit().putFloat("line_height_multiplier", Float.NaN).apply()
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        prefs.edit().putFloat("line_height_multiplier", Float.POSITIVE_INFINITY).apply()
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)

        prefs.edit().putFloat("line_height_multiplier", Float.NEGATIVE_INFINITY).apply()
        assertEquals(ReaderPreferences.DEFAULT_LINE_HEIGHT, readerPrefs.lineHeightMultiplier(), 0.01f)
    }

    @Test
    fun themeAndNightMode_persistence() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(ReaderPreferences.THEME_PAPER, readerPrefs.theme())
        assertFalse(readerPrefs.nightMode())

        readerPrefs.setNightMode(true)
        assertEquals(ReaderPreferences.THEME_NIGHT, readerPrefs.theme())
        assertTrue(readerPrefs.nightMode())

        readerPrefs.setTheme(ReaderPreferences.THEME_EYE_CARE)
        assertEquals(ReaderPreferences.THEME_EYE_CARE, readerPrefs.theme())
        assertFalse(readerPrefs.nightMode())
    }

    @Test
    fun margin_defaultWhenAbsentAndClamping() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        // Absent key → standard (historical pad look for prior installs).
        assertFalse(prefs.contains("reader_margin"))
        assertEquals(ReaderPreferences.MARGIN_STANDARD, readerPrefs.margin())

        readerPrefs.setMargin(ReaderPreferences.MARGIN_NARROW)
        assertEquals(ReaderPreferences.MARGIN_NARROW, readerPrefs.margin())

        readerPrefs.setMargin(ReaderPreferences.MARGIN_WIDE)
        assertEquals(ReaderPreferences.MARGIN_WIDE, readerPrefs.margin())

        readerPrefs.setMargin(-3)
        assertEquals(ReaderPreferences.MARGIN_NARROW, readerPrefs.margin())

        readerPrefs.setMargin(99)
        assertEquals(ReaderPreferences.MARGIN_WIDE, readerPrefs.margin())
    }
}
