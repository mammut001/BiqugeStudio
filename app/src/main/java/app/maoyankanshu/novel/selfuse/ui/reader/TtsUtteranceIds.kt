package app.maoyankanshu.novel.selfuse.ui.reader

/** Parse helpers for `synth-$gen-$end` / `speak-$gen-$end` utterance ids. */
object TtsUtteranceIds {
    fun synth(gen: Int, endExclusive: Int): String = "synth-$gen-$endExclusive"

    fun speak(gen: Int, endExclusive: Int): String = "speak-$gen-$endExclusive"

    fun isSynth(id: String): Boolean = id.startsWith("synth-")

    fun isSpeak(id: String): Boolean = id.startsWith("speak-")

    /** Speak-generation embedded in the utterance id, or null if malformed. */
    fun parseGeneration(utteranceId: String): Int? {
        val parts = utteranceId.split('-')
        if (parts.size < 3) return null
        if (parts[0] != "synth" && parts[0] != "speak") return null
        return parts[1].toIntOrNull()
    }
}
