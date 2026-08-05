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
        private val stringMap = HashMap<String, String>()

        override fun getAll(): MutableMap<String, *> = HashMap<String, Any>()
        override fun getString(key: String?, defValue: String?): String? = stringMap[key] ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
        override fun getInt(key: String?, defValue: Int): Int = intMap[key] ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = defValue
        override fun getFloat(key: String?, defValue: Float): Float = floatMap[key] ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = boolMap[key] ?: defValue
        override fun contains(key: String?): Boolean =
            intMap.containsKey(key) || floatMap.containsKey(key) ||
                boolMap.containsKey(key) || stringMap.containsKey(key)

        override fun edit(): SharedPreferences.Editor = TestEditor(this)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class TestEditor(private val prefs: TestSharedPreferences) : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) {
                    if (value == null) prefs.stringMap.remove(key) else prefs.stringMap[key] = value
                }
                return this
            }
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
                    prefs.stringMap.remove(it)
                }
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                prefs.intMap.clear()
                prefs.floatMap.clear()
                prefs.boolMap.clear()
                prefs.stringMap.clear()
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

        // Expanded paper themes stay in range and soft-night counts as night mode.
        readerPrefs.setTheme(ReaderPreferences.THEME_GREEN)
        assertEquals(ReaderPreferences.THEME_GREEN, readerPrefs.theme())
        assertFalse(readerPrefs.nightMode())

        readerPrefs.setTheme(ReaderPreferences.THEME_SOFT_NIGHT)
        assertEquals(ReaderPreferences.THEME_SOFT_NIGHT, readerPrefs.theme())
        assertTrue(readerPrefs.nightMode())

        readerPrefs.setTheme(99)
        assertEquals(ReaderPreferences.THEME_SOFT_NIGHT, readerPrefs.theme())
    }

    @Test
    fun ttsRate_defaultAndClamping() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(1f, readerPrefs.ttsRate(), 0.01f)

        readerPrefs.setTtsRate(0.1f)
        assertEquals(0.5f, readerPrefs.ttsRate(), 0.01f)

        readerPrefs.setTtsRate(9f)
        assertEquals(2f, readerPrefs.ttsRate(), 0.01f)

        readerPrefs.setTtsRate(1.25f)
        assertEquals(1.25f, readerPrefs.ttsRate(), 0.01f)
    }

    @Test
    fun ttsEnginePackage_defaultEmptyAndPersist() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals("", readerPrefs.ttsEnginePackage())
        readerPrefs.setTtsEnginePackage("com.google.android.tts")
        assertEquals("com.google.android.tts", readerPrefs.ttsEnginePackage())
        readerPrefs.setTtsEnginePackage(null)
        assertEquals("", readerPrefs.ttsEnginePackage())
        readerPrefs.setTtsEnginePackage("  com.iflytek.speechsuite  ")
        assertEquals("com.iflytek.speechsuite", readerPrefs.ttsEnginePackage())
    }

    @Test
    fun autoPageTurnSec_defaultOffAndClamp() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(0, readerPrefs.autoPageTurnSec())
        readerPrefs.setAutoPageTurnSec(30)
        assertEquals(30, readerPrefs.autoPageTurnSec())
        readerPrefs.setAutoPageTurnSec(-3)
        assertEquals(0, readerPrefs.autoPageTurnSec())
        readerPrefs.setAutoPageTurnSec(999)
        assertEquals(300, readerPrefs.autoPageTurnSec())
    }

    @Test
    fun fontFamily_keepScreenOn_volumePageTurn_defaultsAndClamp() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)

        assertEquals(ReaderPreferences.FONT_SERIF, readerPrefs.fontFamily())
        readerPrefs.setFontFamily(ReaderPreferences.FONT_SANS)
        assertEquals(ReaderPreferences.FONT_SANS, readerPrefs.fontFamily())
        readerPrefs.setFontFamily(-1)
        assertEquals(ReaderPreferences.FONT_SERIF, readerPrefs.fontFamily())
        readerPrefs.setFontFamily(99)
        assertEquals(ReaderPreferences.FONT_CUSTOM, readerPrefs.fontFamily())

        assertTrue(readerPrefs.keepScreenOn())
        readerPrefs.setKeepScreenOn(false)
        assertFalse(readerPrefs.keepScreenOn())

        assertTrue(readerPrefs.volumePageTurn())
        readerPrefs.setVolumePageTurn(false)
        assertFalse(readerPrefs.volumePageTurn())

        assertTrue(readerPrefs.pageTurnAnimation())
        readerPrefs.setPageTurnAnimation(false)
        assertFalse(readerPrefs.pageTurnAnimation())

        assertTrue(readerPrefs.paragraphIndent())
        readerPrefs.setParagraphIndent(false)
        assertFalse(readerPrefs.paragraphIndent())

        assertFalse(readerPrefs.autoNight())
        readerPrefs.setAutoNight(true)
        assertTrue(readerPrefs.autoNight())
        assertEquals(19, readerPrefs.autoNightStartHour())
        assertEquals(7, readerPrefs.autoNightEndHour())

        readerPrefs.setCustomFontName("MyFont.ttf")
        assertEquals("MyFont.ttf", readerPrefs.customFontName())
        readerPrefs.setFontFamily(ReaderPreferences.FONT_CUSTOM)
        readerPrefs.clearCustomFont()
        assertEquals("", readerPrefs.customFontName())
        assertEquals(ReaderPreferences.FONT_SERIF, readerPrefs.fontFamily())
    }

    @Test
    fun brightness_systemAndClamp() {
        val prefs = TestSharedPreferences()
        val readerPrefs = ReaderPreferences.get(prefs)
        assertEquals(-1f, readerPrefs.brightness(), 0.001f)
        readerPrefs.setBrightness(0.5f)
        assertEquals(0.5f, readerPrefs.brightness(), 0.001f)
        readerPrefs.setBrightness(0.01f)
        assertEquals(0.08f, readerPrefs.brightness(), 0.001f)
        readerPrefs.setBrightness(2f)
        assertEquals(1f, readerPrefs.brightness(), 0.001f)
        readerPrefs.setBrightness(-1f)
        assertEquals(-1f, readerPrefs.brightness(), 0.001f)
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
