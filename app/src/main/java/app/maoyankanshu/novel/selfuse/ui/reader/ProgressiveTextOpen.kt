package app.maoyankanshu.novel.selfuse.ui.reader

import java.nio.charset.StandardCharsets

/**
 * Pure helpers for progressive large-TXT open: decode only a bounded byte window
 * around saved progress so the first readable body does not wait on a full multi‑MB
 * [String] allocation.
 *
 * Fully JVM unit-testable — no Android framework types.
 */
object ProgressiveTextOpen {
    /**
     * First-window size (~48 KiB). Enough for many screens of Chinese text while
     * keeping decode/alloc work O(window), not O(file).
     */
    const val FIRST_WINDOW_BYTES: Int = 48 * 1024

    /** Files at or above this size open via first-window preview then full decode. */
    const val PROGRESSIVE_BYTE_THRESHOLD: Int = 512 * 1024

    fun shouldOpenProgressively(fileSizeBytes: Long): Boolean =
        fileSizeBytes >= PROGRESSIVE_BYTE_THRESHOLD

    /**
     * Map library progress (0…1000) onto a byte offset inside [fileSize].
     * Empty / non-positive size → 0.
     */
    fun byteOffsetForProgress(fileSize: Long, progress: Int): Long {
        if (fileSize <= 0L) return 0L
        if (fileSize == 1L) return 0L
        val p = ProgressMath.clampProgress(progress)
        return ((p / 1000.0) * (fileSize - 1L).toDouble()).toLong().coerceIn(0L, fileSize - 1L)
    }

    /**
     * Half-open byte range `[start, endExclusive)` of size up to [windowBytes] centered
     * on [centerByte], clamped to the file.
     */
    fun windowByteRange(fileSize: Long, centerByte: Long, windowBytes: Int): IntRange {
        if (fileSize <= 0L) return 0 until 0
        val size = fileSize.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val window = windowBytes.coerceAtLeast(1)
        if (size <= window) return 0 until size
        val center = centerByte.coerceIn(0L, (size - 1).toLong()).toInt()
        val half = window / 2
        var start = (center - half).coerceAtLeast(0)
        var end = (start + window).coerceAtMost(size)
        if (end - start < window) {
            start = (end - window).coerceAtLeast(0)
        }
        return start until end
    }

    /**
     * Walk backward from [index] so the slice does not start on a UTF-8 continuation byte.
     */
    fun alignUtf8Start(bytes: ByteArray, index: Int): Int {
        if (bytes.isEmpty()) return 0
        var i = index.coerceIn(0, bytes.size)
        while (i > 0 && isUtf8Continuation(bytes[i])) {
            i--
        }
        return i
    }

    /**
     * Walk backward from [endExclusive] so the slice does not end mid multi-byte sequence.
     */
    fun alignUtf8End(bytes: ByteArray, start: Int, endExclusive: Int): Int {
        if (bytes.isEmpty()) return 0
        val s = start.coerceIn(0, bytes.size)
        var e = endExclusive.coerceIn(s, bytes.size)
        if (e <= s) return s
        // If e is mid-sequence, pull back to the start of that incomplete character.
        if (e < bytes.size && isUtf8Continuation(bytes[e])) {
            var i = e
            while (i > s && isUtf8Continuation(bytes[i])) {
                i--
            }
            // i is a lead byte of an incomplete char at the cut — drop it.
            if (i >= s && !isUtf8Continuation(bytes[i])) {
                e = i
            }
        }
        return e.coerceAtLeast(s)
    }

    /**
     * Decode a UTF-8 slice with boundary alignment. Allocations are O(end-start), not O(file).
     */
    fun decodeUtf8Slice(bytes: ByteArray, start: Int, endExclusive: Int): String {
        if (bytes.isEmpty()) return ""
        val s = alignUtf8Start(bytes, start)
        val e = alignUtf8End(bytes, s, endExclusive)
        if (s >= e) return ""
        return String(bytes, s, e - s, StandardCharsets.UTF_8)
    }

    /**
     * First readable text window around [progress] without decoding the whole file.
     *
     * Work is O([windowBytes]) relative to book size — suitable for multi‑MB class
     * fixtures on the open critical path.
     */
    fun firstWindowText(
        fileBytes: ByteArray,
        progress: Int,
        windowBytes: Int = FIRST_WINDOW_BYTES,
    ): String {
        if (fileBytes.isEmpty()) return ""
        if (fileBytes.size <= windowBytes.coerceAtLeast(1)) {
            return String(fileBytes, StandardCharsets.UTF_8)
        }
        val center = byteOffsetForProgress(fileBytes.size.toLong(), progress)
        val range = windowByteRange(fileBytes.size.toLong(), center, windowBytes)
        return decodeUtf8Slice(fileBytes, range.first, range.last + 1)
    }

    /**
     * Full UTF-8 decode of [fileBytes]. Kept as a named helper so open/load tests drive
     * the same entry used by the activity path.
     */
    fun decodeFullText(fileBytes: ByteArray): String {
        if (fileBytes.isEmpty()) return ""
        return String(fileBytes, StandardCharsets.UTF_8)
    }

    private fun isUtf8Continuation(b: Byte): Boolean =
        (b.toInt() and 0xC0) == 0x80
}
