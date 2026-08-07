package app.maoyankanshu.novel.selfuse.ui.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/** Voice source filter shown in the in-reader voice manager. */
enum class TtsVoiceFilter {
    ALL,
    LOCAL,
    NETWORK,
}

/** A UI-safe description of one voice exposed by the active TTS engine. */
data class TtsVoiceOption(
    val name: String,
    val label: String,
    val localeTag: String,
    val networkRequired: Boolean,
    val quality: Int,
    val latency: Int,
)

/** Maps Android voices into stable, readable options for the reader settings sheet. */
object TtsVoiceCatalog {
    fun fromTextToSpeech(tts: TextToSpeech): List<TtsVoiceOption> {
        val voices = try {
            tts.voices.orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        return voices
            .map(::fromVoice)
            .distinctBy { it.name }
            .sortedWith(
                compareByDescending<TtsVoiceOption> { isChinese(it.localeTag) }
                    .thenBy { it.networkRequired }
                    .thenByDescending { it.quality }
                    .thenBy { it.latency }
                    .thenBy { it.label },
            )
    }

    /**
     * Filters voices without touching Android framework objects, so the sheet stays cheap and
     * the matching behavior remains unit-testable on the JVM.
     */
    fun filter(
        voices: List<TtsVoiceOption>,
        query: String,
        source: TtsVoiceFilter,
    ): List<TtsVoiceOption> {
        val needle = query.trim().lowercase(Locale.ROOT)
        return voices.filter { voice ->
            val sourceMatches = when (source) {
                TtsVoiceFilter.ALL -> true
                TtsVoiceFilter.LOCAL -> !voice.networkRequired
                TtsVoiceFilter.NETWORK -> voice.networkRequired
            }
            if (!sourceMatches) return@filter false
            if (needle.isEmpty()) return@filter true
            voice.label.lowercase(Locale.ROOT).contains(needle) ||
                voice.name.lowercase(Locale.ROOT).contains(needle) ||
                voice.localeTag.lowercase(Locale.ROOT).contains(needle)
        }
    }

    private fun fromVoice(voice: Voice): TtsVoiceOption {
        val locale = voice.locale
        val tag = locale.toLanguageTag().ifBlank { locale.toString() }
        val localeLabel = locale.displayName.ifBlank { tag }
        val voiceLabel = voice.name.substringAfterLast('/').ifBlank { voice.name }
        val label = if (voiceLabel.equals(tag, ignoreCase = true)) {
            localeLabel
        } else {
            "$localeLabel · $voiceLabel"
        }
        val network = if (voice.isNetworkConnectionRequired) " · 在线" else " · 本机"
        return TtsVoiceOption(
            name = voice.name,
            label = label + network,
            localeTag = tag,
            networkRequired = voice.isNetworkConnectionRequired,
            quality = voice.quality,
            latency = voice.latency,
        )
    }

    private fun isChinese(localeTag: String): Boolean {
        val language = localeTag.substringBefore('-').substringBefore('_')
        return language.equals(Locale.CHINESE.language, ignoreCase = true) ||
            language.equals("cmn", ignoreCase = true) ||
            language.equals("yue", ignoreCase = true)
    }
}
