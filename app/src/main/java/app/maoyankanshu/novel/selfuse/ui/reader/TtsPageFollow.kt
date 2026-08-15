package app.maoyankanshu.novel.selfuse.ui.reader

import kotlin.math.roundToLong

/** One timed page turn while a single TTS audio chunk is already playing. */
data class TtsPageCue(
    val fromPage: Int,
    val page: Int,
    val boundaryOffset: Int,
    val atMillis: Long,
)

/** Playback window reported by app-owned synthesized-audio playback. */
data class TtsChunkPlayback(
    val start: Int,
    val endExclusive: Int,
    val durationMs: Long,
    val startedElapsedRealtimeMs: Long,
)

/**
 * Pure timing helpers for following TTS across page boundaries without splitting the spoken chunk.
 *
 * The controller keeps natural paragraph/sentence chunks. Once synthesized audio starts and its
 * duration is known, page boundaries inside that chunk are mapped proportionally onto playback
 * time. This is an estimate (TTS does not expose character timing for app-owned WAV playback), but
 * it lets the reader advance mid-paragraph instead of waiting for the next chunk.
 */
object TtsPageFollow {
    fun cuesForApproximatePages(
        textLength: Int,
        charsPerPage: Int,
        chunkStart: Int,
        chunkEndExclusive: Int,
        durationMs: Long,
    ): List<TtsPageCue> {
        val length = textLength.coerceAtLeast(0)
        if (length <= 0 || durationMs <= 1L) return emptyList()
        val size = charsPerPage.coerceAtLeast(PageIndex.MIN_APPROX_CHARS_PER_PAGE)
        val start = chunkStart.coerceIn(0, length)
        val end = chunkEndExclusive.coerceIn(start, length)
        if (end - start <= 1) return emptyList()

        val firstBoundary = ((start / size) + 1) * size
        if (firstBoundary >= end || firstBoundary >= length) return emptyList()

        val out = ArrayList<TtsPageCue>()
        var boundary = firstBoundary
        while (boundary < end && boundary < length) {
            val toPage = boundary / size
            val fromPage = (boundary - 1).coerceAtLeast(0) / size
            out += TtsPageCue(
                fromPage = fromPage,
                page = toPage,
                boundaryOffset = boundary,
                atMillis = cueTime(start, end, boundary, durationMs),
            )
            boundary += size
        }
        return out
    }

    fun cuesForExactPages(
        pageStarts: List<Int>,
        textLength: Int,
        chunkStart: Int,
        chunkEndExclusive: Int,
        durationMs: Long,
    ): List<TtsPageCue> {
        val length = textLength.coerceAtLeast(0)
        if (length <= 0 || durationMs <= 1L || pageStarts.size <= 1) return emptyList()
        val start = chunkStart.coerceIn(0, length)
        val end = chunkEndExclusive.coerceIn(start, length)
        if (end - start <= 1) return emptyList()

        val out = ArrayList<TtsPageCue>()
        for (page in 1 until pageStarts.size) {
            val boundary = pageStarts[page].coerceIn(0, length)
            if (boundary <= start) continue
            if (boundary >= end) break
            out += TtsPageCue(
                fromPage = page - 1,
                page = page,
                boundaryOffset = boundary,
                atMillis = cueTime(start, end, boundary, durationMs),
            )
        }
        return out
    }

    private fun cueTime(start: Int, end: Int, boundary: Int, durationMs: Long): Long {
        val span = (end - start).coerceAtLeast(1)
        val covered = (boundary - start).coerceIn(0, span)
        return ((covered.toDouble() / span.toDouble()) * durationMs.toDouble())
            .roundToLong()
            .coerceIn(1L, durationMs - 1L)
    }
}
