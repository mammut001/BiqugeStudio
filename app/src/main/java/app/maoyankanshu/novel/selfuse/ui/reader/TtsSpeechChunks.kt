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
     * Inclusive [IntRange] of the visible paragraph that contains [offset].
     *
     * Paragraph-leading/trailing whitespace is excluded intentionally. Imported novels often
     * contain two ASCII/full-width spaces before every paragraph while the reader also renders
     * a first-line indent. Including those invisible characters in a background span makes the
     * active TTS paragraph look like a solid rectangular block starting at the left edge.
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

        // Keep indentation in layout/text, but never paint it as part of the TTS highlight.
        while (start < endExclusive && text[start].isWhitespace()) start++
        while (endExclusive > start && text[endExclusive - 1].isWhitespace()) endExclusive--
        return if (endExclusive > start) start until endExclusive else IntRange.EMPTY
    }

    /**
     * Paragraph start suitable for a user-initiated TTS seek. Leading whitespace is skipped;
     * whitespace-only paragraphs return null so a tap never starts an empty utterance.
     */
    fun paragraphSpeechStart(text: String, offset: Int): Int? {
        val range = paragraphRangeContaining(text, offset)
        if (range.isEmpty()) return null
        return range.first
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

/**
 * Watchdog timing for app-owned synthesized playback.
 *
 * When the actual WAV/player duration is known, use it instead of guessing from character
 * count. The old short character budget could expire before a slow Chinese utterance ended,
 * stopping the current sentence and advancing to the next chunk.
 */
object TtsPlaybackWatchdog {
    private const val MIN_TIMEOUT_MS = 8_000L
    private const val COMPLETION_GRACE_MS = 5_000L
    private const val FALLBACK_BASE_MS = 15_000L
    private const val FALLBACK_PER_CHAR_MS = 750L
    private const val MAX_TIMEOUT_MS = 8 * 60_000L

    fun timeoutMs(knownDurationMs: Long?, chars: Int): Long {
        val duration = knownDurationMs?.takeIf { it > 0L }
        val raw = if (duration != null) {
            duration + COMPLETION_GRACE_MS
        } else {
            FALLBACK_BASE_MS + chars.coerceAtLeast(1) * FALLBACK_PER_CHAR_MS
        }
        return raw.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
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
