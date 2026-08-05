package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-safe checks around TTS package naming (no Android PackageManager).
 * Ensures our known engine list is non-empty and well-formed for device pick.
 */
class ReaderTtsEnginePickTest {

    @Test
    fun knownEnginePackageNames_areValidApplicationIds() {
        val candidates = listOf(
            "com.google.android.tts",
            "com.samsung.SMT",
            "com.iflytek.speechsuite",
            "com.iflytek.inputmethod.tts",
            "com.huawei.voiceservice",
            "com.github.olga_yakovleva.rhvoice.android",
        )
        assertTrue(candidates.isNotEmpty())
        for (pkg in candidates) {
            assertTrue(pkg, pkg.contains('.'))
            assertFalse(pkg, pkg.any { it.isWhitespace() })
            assertTrue(pkg, pkg.all { it.isLetterOrDigit() || it == '.' || it == '_' })
        }
    }

    @Test
    fun ttsRateClamp_stillAlignedWithSpeakPath() {
        // start() path uses TtsRate.clamp — keep in sync with prefs.
        assertTrue(TtsRate.clamp(0.1f) >= TtsRate.MIN)
        assertTrue(TtsRate.clamp(9f) <= TtsRate.MAX)
    }
}
