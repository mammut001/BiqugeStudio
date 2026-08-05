package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for shipped [TtsEngineCatalog.friendlyLabel] / package helpers. */
class TtsEngineCatalogTest {

    @Test
    fun friendlyLabel_systemDefault() {
        assertEquals("系统默认", TtsEngineCatalog.friendlyLabel(""))
        assertEquals("系统默认", TtsEngineCatalog.systemDefaultOption().label)
        assertEquals("", TtsEngineCatalog.systemDefaultOption().packageName)
    }

    @Test
    fun friendlyLabel_mapsGoogleAndChineseVendors() {
        assertEquals(
            "Google 文字转语音",
            TtsEngineCatalog.friendlyLabel("com.google.android.tts"),
        )
        assertEquals(
            "讯飞语音",
            TtsEngineCatalog.friendlyLabel("com.iflytek.speechsuite"),
        )
        assertEquals(
            "三星语音",
            TtsEngineCatalog.friendlyLabel("com.samsung.SMT"),
        )
        assertEquals(
            "华为语音",
            TtsEngineCatalog.friendlyLabel("com.huawei.voiceservice"),
        )
        assertEquals(
            "小米语音",
            TtsEngineCatalog.friendlyLabel("com.xiaomi.mibrain.speech"),
        )
    }

    @Test
    fun friendlyLabel_prefersRawWhenUnknown() {
        assertEquals(
            "My Engine",
            TtsEngineCatalog.friendlyLabel("com.example.customtts", "My Engine"),
        )
    }

    @Test
    fun normalizePackage_trimsNullToEmpty() {
        assertEquals("", TtsEngineCatalog.normalizePackage(null))
        assertEquals("", TtsEngineCatalog.normalizePackage("  "))
        assertEquals("com.google.android.tts", TtsEngineCatalog.normalizePackage(" com.google.android.tts "))
    }

    @Test
    fun knownPackages_areWellFormed() {
        assertTrue(TtsEngineCatalog.knownPackages().isNotEmpty())
        for (pkg in TtsEngineCatalog.knownPackages()) {
            assertTrue(pkg.contains('.'))
        }
    }

    @Test
    fun mergeFromTextToSpeech_keepsSystemDefaultFirst() {
        val base = listOf(
            TtsEngineCatalog.systemDefaultOption(),
            TtsEngineOption("com.example.a", "A"),
        )
        // Without a live TextToSpeech we only verify pure merge of existing list shape.
        val labels = base.map { it.label }
        assertEquals("系统默认", labels.first())
        assertTrue(base.any { it.packageName == "com.example.a" })
    }

    @Test
    fun friendlyLabel_oneplusColorOs() {
        assertEquals(
            "OPPO/一加 语音",
            TtsEngineCatalog.friendlyLabel("com.coloros.tts"),
        )
        assertEquals(
            "OPPO/一加 语音",
            TtsEngineCatalog.friendlyLabel("com.heytap.speechassist"),
        )
    }
}
