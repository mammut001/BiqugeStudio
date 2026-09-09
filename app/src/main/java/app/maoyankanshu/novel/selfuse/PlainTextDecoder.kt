package app.maoyankanshu.novel.selfuse

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * Plain TXT body decoding with BOM detection (shared by local and remote import).
 *
 * Order: UTF-32 LE/BE BOM (before UTF-16, since UTF-32LE starts with FF FE) →
 * UTF-16 LE/BE BOM → UTF-8 BOM strip → strict UTF-8, or GB18030 when the byte stream is
 * actually malformed as UTF-8. UTF-32 uses guarded [Charset.forName] for minSdk 23 / host JVM
 * variance.
 */
object PlainTextDecoder {

    fun decode(data: ByteArray): String {
        if (data.isEmpty()) return ""

        // UTF-32LE BOM: FF FE 00 00 — must precede UTF-16LE (FF FE).
        if (isUtf32LeBom(data)) {
            charsetOrNull("UTF-32LE")?.let { cs ->
                return String(data, 4, data.size - 4, cs)
            }
            // Charset unavailable: strip BOM; do not treat as UTF-16LE.
            return decodeUtf8OrGb(data, 4)
        }

        // UTF-32BE BOM: 00 00 FE FF
        if (isUtf32BeBom(data)) {
            charsetOrNull("UTF-32BE")?.let { cs ->
                return String(data, 4, data.size - 4, cs)
            }
            return decodeUtf8OrGb(data, 4)
        }

        // UTF-16LE BOM: FF FE
        if (data.size >= 2 &&
            (data[0].toInt() and 0xff) == 0xff &&
            (data[1].toInt() and 0xff) == 0xfe
        ) {
            return String(data, 2, data.size - 2, Charset.forName("UTF-16LE"))
        }

        // UTF-16BE BOM: FE FF
        if (data.size >= 2 &&
            (data[0].toInt() and 0xff) == 0xfe &&
            (data[1].toInt() and 0xff) == 0xff
        ) {
            return String(data, 2, data.size - 2, Charset.forName("UTF-16BE"))
        }

        val offset =
            if (data.size >= 3 &&
                (data[0].toInt() and 0xff) == 0xef &&
                (data[1].toInt() and 0xff) == 0xbb &&
                (data[2].toInt() and 0xff) == 0xbf
            ) {
                3
            } else {
                0
            }
        // BOM-less UTF-16: strict UTF-8 fails and GB18030 then emits CJK + NUL
        // confetti. Detect the NUL-interleave pattern first — even positions NUL
        // means UTF-16BE, odd positions NUL means UTF-16LE.
        detectBomLessUtf16(data, offset)?.let { cs ->
            return String(data, offset, data.size - offset, cs)
        }
        return decodeUtf8OrGb(data, offset)
    }

    /**
     * Heuristic for BOM-less UTF-16 (common in Windows-saved Chinese TXT).
     * Counts NUL bytes on even/odd positions over a bounded prefix; a strong
     * majority on one parity with enough NULs total means UTF-16. Pure for tests.
     */
    internal fun detectBomLessUtf16(data: ByteArray, offset: Int): Charset? {
        val size = data.size - offset
        if (size < 4) return null
        // Need strict-UTF-8 to actually fail, otherwise plain UTF-8/GBK wins.
        if (isValidUtf8(data, offset)) return null
        val sample = minOf(size, 1024)
        var evenNul = 0
        var oddNul = 0
        var i = 0
        while (i < sample) {
            if ((data[offset + i].toInt() and 0xff) == 0x00) {
                if (i % 2 == 0) evenNul++ else oddNul++
            }
            i++
        }
        val total = evenNul + oddNul
        // At least ~10% NUL density and a 4:1 parity majority avoids flagging
        // binary/GB text that merely contains a stray zero.
        if (total * 10 < sample) return null
        if (evenNul >= oddNul * 4 && evenNul >= 4) {
            return charsetOrNull("UTF-16BE")
        }
        if (oddNul >= evenNul * 4 && oddNul >= 4) {
            return charsetOrNull("UTF-16LE")
        }
        return null
    }

    private fun isValidUtf8(data: ByteArray, offset: Int): Boolean {
        if (offset >= data.size) return true
        val decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(ByteBuffer.wrap(data, offset, data.size - offset))
            true
        } catch (_: CharacterCodingException) {
            false
        }
    }

    /** Pure helpers for tests and [EpubReader] alignment. */
    fun isUtf32LeBom(data: ByteArray): Boolean =
        data.size >= 4 &&
            (data[0].toInt() and 0xff) == 0xff &&
            (data[1].toInt() and 0xff) == 0xfe &&
            (data[2].toInt() and 0xff) == 0x00 &&
            (data[3].toInt() and 0xff) == 0x00

    fun isUtf32BeBom(data: ByteArray): Boolean =
        data.size >= 4 &&
            (data[0].toInt() and 0xff) == 0x00 &&
            (data[1].toInt() and 0xff) == 0x00 &&
            (data[2].toInt() and 0xff) == 0xfe &&
            (data[3].toInt() and 0xff) == 0xff

    private fun decodeUtf8OrGb(data: ByteArray, offset: Int): String {
        if (offset >= data.size) return ""
        val utf8 = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            utf8.decode(ByteBuffer.wrap(data, offset, data.size - offset)).toString()
        } catch (_: CharacterCodingException) {
            String(data, offset, data.size - offset, Charset.forName("GB18030"))
        }
    }

    private fun charsetOrNull(name: String): Charset? =
        try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }
}
