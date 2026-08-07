package app.maoyankanshu.novel.selfuse.ui.reader

/**
 * Pure helpers for continuous TTS chunking.
 *
 * OEM [android.speech.tts.TextToSpeech.speak] can stutter or block when handed multi‑KB
 * strings; short sentence-sized chunks keep the main thread responsive and play nicer
 * with [android.speech.tts.UtteranceProgressListener] continuation.
 *
 * Chunking prefers **paragraph** boundaries (`\n`) so in-page highlight can track
 * one spoken paragraph at a time without character-level TTS callbacks.
 */
object TtsSpeechChunks {
    /** Hard cap per speak() call (characters). */
    const val MAX_CHUNK_CHARS: Int = 480

    /** Prefer not to break before at least this many chars (unless near end / paragraph). */
    const val MIN_BREAK_CHARS: Int = 100

    /**
     * Exclusive end index of the next TTS chunk starting at [offset] in [text].
     *
     * Order of preference inside the max window:
     * 1. First paragraph break (`\n`) — short paragraphs are allowed
     * 2. Chinese/ASCII sentence terminators (after [MIN_BREAK_CHARS])
     * 3. Comma / space
     * 4. Hard cut at [MAX_CHUNK_CHARS]
     */
    fun nextChunkEnd(text: String, offset: Int): Int {
        if (text.isEmpty()) return 0
        val start = offset.coerceIn(0, text.length)
        if (start >= text.length) return text.length
        val hardEnd = (start + MAX_CHUNK_CHARS).coerceAtMost(text.length)

        // Prefer the first paragraph end within the window (may be shorter than MIN_BREAK),
        // including when the remaining text is shorter than MAX_CHUNK_CHARS.
        for (i in start until hardEnd) {
            if (text[i] == '\n') return i + 1
        }
        if (hardEnd >= text.length) return text.length

        val minBreak = (start + MIN_BREAK_CHARS).coerceAtMost(hardEnd)
        var breakAt = -1
        for (i in hardEnd - 1 downTo minBreak) {
            when (text[i]) {
                '。', '！', '？', '；', '.', '!', '?', ';' -> {
                    breakAt = i + 1
                    break
                }
            }
        }
        if (breakAt > start) return breakAt

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

    /**
     * Inclusive [IntRange] of the paragraph that contains [offset]
     * (from after the previous `\n` through the char before the next `\n`,
     * stripping a trailing `\r` from `\r\n` line endings).
     */
    fun paragraphRangeContaining(text: String, offset: Int): IntRange {
        if (text.isEmpty()) return IntRange.EMPTY
        // Standing on a newline belongs to the *following* paragraph (or empty).
        val anchor = when {
            offset < 0 -> 0
            offset >= text.length -> text.length - 1
            text[offset] == '\n' -> {
                if (offset + 1 < text.length) offset + 1 else offset
            }
            else -> offset
        }
        var start = anchor
        while (start > 0 && text[start - 1] != '\n') {
            start--
        }
        var endExclusive = anchor
        while (endExclusive < text.length && text[endExclusive] != '\n') {
            endExclusive++
        }
        // Drop CR from Windows `\r\n` so highlight does not paint a control char.
        if (endExclusive > start && text[endExclusive - 1] == '\r') {
            endExclusive--
        }
        if (endExclusive <= start) {
            val end = (start + 1).coerceAtMost(text.length)
            return if (end > start) start until end else IntRange.EMPTY
        }
        return start until endExclusive
    }

    /**
     * Trims whitespace from both ends of `[start, endExclusive)` inside [text],
     * returning the audible span. Empty when the slice is only whitespace.
     */
    fun trimmedChunkRange(text: String, start: Int, endExclusive: Int): IntRange {
        var s = start.coerceIn(0, text.length)
        var e = endExclusive.coerceIn(s, text.length)
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        return if (e > s) s until e else IntRange.EMPTY
    }
}

/** Pure page-overlap math for TTS follow highlight (JVM-testable). */
object TtsFollowHighlight {
    /**
     * Local inclusive range inside a page slice for [highlight], or null if none.
     * [pageStartOffset] is the book-absolute offset of `pageBody[0]`.
     */
    fun overlapInPage(
        pageStartOffset: Int,
        pageLength: Int,
        highlight: IntRange?,
    ): IntRange? {
        if (pageLength <= 0 || highlight == null || highlight.isEmpty()) return null
        val pageEnd = pageStartOffset + pageLength
        val hStart = highlight.first
        val hEndExclusive = highlight.last + 1
        val overlapStart = maxOf(hStart, pageStartOffset)
        val overlapEnd = minOf(hEndExclusive, pageEnd)
        if (overlapStart >= overlapEnd) return null
        val localStart = overlapStart - pageStartOffset
        val localEndExclusive = overlapEnd - pageStartOffset
        return localStart until localEndExclusive
    }
}
