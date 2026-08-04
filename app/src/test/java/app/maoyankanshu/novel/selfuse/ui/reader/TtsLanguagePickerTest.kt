package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TtsLanguagePickerTest {

    @Test
    fun preferredLocales_startsWithChinese() {
        val list = TtsLanguagePicker.preferredLocales()
        assertTrue(list.isNotEmpty())
        val first = list.first()
        assertTrue(
            first.language.equals("zh", ignoreCase = true) ||
                first == Locale.CHINESE ||
                first == Locale.SIMPLIFIED_CHINESE ||
                first == Locale.CHINA,
        )
    }

    @Test
    fun isUsable_matchesTtsConstants() {
        // TextToSpeech.LANG_AVAILABLE = 0, COUNTRY = 1, COUNTRY_VAR = 2
        assertTrue(TtsLanguagePicker.isUsable(0))
        assertTrue(TtsLanguagePicker.isUsable(1))
        assertTrue(TtsLanguagePicker.isUsable(2))
        // LANG_MISSING_DATA = -1, LANG_NOT_SUPPORTED = -2
        assertFalse(TtsLanguagePicker.isUsable(-1))
        assertFalse(TtsLanguagePicker.isUsable(-2))
    }
}
