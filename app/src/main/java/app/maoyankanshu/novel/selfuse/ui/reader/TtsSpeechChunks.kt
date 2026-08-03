package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure helpers for continuous TTS chunking.
 *
 * OEM [android.speech.tts.TextToSpeech.speak] can stutter or block when handed multi‑KB
 * strings; short sentence-sized chunks keep the main thread responsive and play nicer
 * with [android.speech.tts.UtteranceProgressListener] continuation.
 */
object TtsSpeechChunks {
    /** Hard cap per speak() call (characters). */
    const val MAX_CHUNK_CHARS: Int = 480

    /** Prefer not to break before at least this many chars (unless near end). */
    const val MIN_BREAK_CHARS: Int = 100

    /**
     * Exclusive end index of the next TTS chunk starting at [offset] in [text].
     * Prefers Chinese/ASCII sentence terminators near the end of the window.
     */
    fun nextChunkEnd(text: String, offset: Int): Int {
        if (text.isEmpty()) return 0
        val start = offset.coerceIn(0, text.length)
        if (start >= text.length) return text.length
        val hardEnd = (start + MAX_CHUNK_CHARS).coerceAtMost(text.length)
        if (hardEnd >= text.length) return text.length

        val minBreak = (start + MIN_BREAK_CHARS).coerceAtMost(hardEnd)
        var breakAt = -1
        // Scan backward from hardEnd for a natural stop.
        for (i in hardEnd - 1 downTo minBreak) {
            when (text[i]) {
                '。', '！', '？', '；', '\n', '.', '!', '?', ';' -> {
                    breakAt = i + 1
                    break
                }
            }
        }
        if (breakAt > start) return breakAt

        // Fallback: break on comma / space if present.
        for (i in hardEnd - 1 downTo minBreak) {
            when (text[i]) {
                '，', ',', '、', ' ' -> {
                    breakAt = i + 1
                    break
                }
            }
        }
        return if (breakAt > start) breakAt else hardEnd
    }
}
