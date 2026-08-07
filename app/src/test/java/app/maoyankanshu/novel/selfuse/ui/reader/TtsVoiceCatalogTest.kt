package app.maoyankanshu.novel.selfuse.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceCatalogTest {

    private val voices = listOf(
        TtsVoiceOption(
            name = "cmn-cn-x-local",
            label = "中文（中国）· 本机",
            localeTag = "cmn-CN",
            networkRequired = false,
            quality = 400,
            latency = 200,
        ),
        TtsVoiceOption(
            name = "en-us-x-network",
            label = "英语（美国）· 在线",
            localeTag = "en-US",
            networkRequired = true,
            quality = 300,
            latency = 100,
        ),
    )

    @Test
    fun filter_bySource() {
        assertEquals(2, TtsVoiceCatalog.filter(voices, "", TtsVoiceFilter.ALL).size)
        assertEquals(1, TtsVoiceCatalog.filter(voices, "", TtsVoiceFilter.LOCAL).size)
        assertEquals("cmn-cn-x-local", TtsVoiceCatalog.filter(voices, "", TtsVoiceFilter.LOCAL).single().name)
        assertEquals(1, TtsVoiceCatalog.filter(voices, "", TtsVoiceFilter.NETWORK).size)
    }

    @Test
    fun filter_matchesLabelNameAndLocale_caseInsensitive() {
        assertEquals(1, TtsVoiceCatalog.filter(voices, "中文", TtsVoiceFilter.ALL).size)
        assertEquals(1, TtsVoiceCatalog.filter(voices, "X-NETWORK", TtsVoiceFilter.ALL).size)
        assertEquals(1, TtsVoiceCatalog.filter(voices, "en-us", TtsVoiceFilter.ALL).size)
        assertTrue(TtsVoiceCatalog.filter(voices, "日语", TtsVoiceFilter.ALL).isEmpty())
    }

    @Test
    fun filter_blankQuery_preservesInputOrder() {
        val result = TtsVoiceCatalog.filter(voices, "  ", TtsVoiceFilter.ALL)
        assertEquals(voices.map { it.name }, result.map { it.name })
    }
}
