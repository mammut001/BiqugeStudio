package app.maoyankanshu.novel.selfuse

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * Plain TXT body decoding with BOM detection (shared by local and remote import).
 *
 * Order: UTF-32 LE/BE BOM (before UTF-16, since UTF-32LE starts with FF FE) →
 * UTF-16 LE/BE BOM → UTF-8 BOM strip → UTF-8, or GB18030 if U+FFFD appears.
 * UTF-32 uses guarded [Charset.forName] for minSdk 23 / host JVM variance.
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
        return decodeUtf8OrGb(data, offset)
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
        val utf8 = String(data, offset, data.size - offset, StandardCharsets.UTF_8)
        return if (utf8.indexOf('\uFFFD') >= 0) {
            String(data, offset, data.size - offset, Charset.forName("GB18030"))
        } else {
            utf8
        }
    }

    private fun charsetOrNull(name: String): Charset? =
        try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }
}
