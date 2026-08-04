package app.maoyankanshu.novel.selfuse.ui.reader

import java.util.Locale

/**
 * Locale preference order for novel TTS (Chinese-first, then device default).
 * Pure helpers — engine binding stays in [ReaderTtsController].
 *
 * Note: This uses the system [android.speech.tts.TextToSpeech] engine (same family
 * of voices Accessibility/TalkBack uses). Driving TalkBack itself via AccessibilityService
 * is not appropriate for continuous in-app reading.
 */
object TtsLanguagePicker {
    /** [android.speech.tts.TextToSpeech.LANG_AVAILABLE] and better are ≥ 0. */
    const val LANG_AVAILABLE: Int = 0

    fun preferredLocales(): List<Locale> = listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.CHINA,
        Locale.CHINESE,
        Locale.TRADITIONAL_CHINESE,
        Locale.TAIWAN,
        Locale.getDefault(),
        Locale.US,
        Locale.ENGLISH,
    )

    /** Whether an [android.speech.tts.TextToSpeech.isLanguageAvailable] / setLanguage result is usable. */
    fun isUsable(resultCode: Int): Boolean = resultCode >= LANG_AVAILABLE
}
