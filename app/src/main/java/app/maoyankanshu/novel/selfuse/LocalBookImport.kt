package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** SAF local TXT / EPUB import helpers (call off the main thread for large files if needed). */
object LocalBookImport {
    private const val MAX_IMPORT_BYTES = 32 * 1024 * 1024

    /** Standard EPUB MIME from SAF / DocumentsUI (optional parameters allowed). */
    const val MIME_EPUB: String = "application/epub+zip"

    data class Imported(
        val title: String,
        val author: String,
        val text: String,
    )

    /**
     * True when the display name ends with `.epub` (any case) or [mimeType] is
     * [MIME_EPUB] (with optional `;…` parameters). Extension and MIME are OR'd so
     * providers that omit the filename extension still import as EPUB.
     */
    fun isEpub(rawName: String?, mimeType: String? = null): Boolean {
        if (rawName != null && rawName.lowercase().endsWith(".epub")) return true
        val mime = mimeType?.trim()?.lowercase() ?: return false
        if (mime == MIME_EPUB) return true
        // e.g. application/epub+zip; charset=binary
        return mime.startsWith("$MIME_EPUB;")
    }

    fun fromStream(
        stream: InputStream,
        rawName: String?,
        defaultName: String,
        authorEpub: String,
        authorTxt: String,
        mimeType: String? = null,
    ): Imported {
        var name = defaultName
        if (!rawName.isNullOrEmpty()) {
            name = rawName.replaceFirst(Regex("\\.[^.]+$"), "")
        }
        val epub = isEpub(rawName, mimeType)
        val boundedStream = BoundedInputStream(stream, MAX_IMPORT_BYTES.toLong())
        val content = try {
            if (epub) EpubReader.read(boundedStream) else readText(boundedStream)
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException(e.message ?: "failed to read book", e)
        }
        if (content.trim().isEmpty()) throw IllegalArgumentException("empty")
        return Imported(
            title = name,
            author = if (epub) authorEpub else authorTxt,
            text = content,
        )
    }

    fun queryDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            val name = cursor.getString(index)
                            if (!name.isNullOrEmpty()) return name
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return uri.lastPathSegment
    }

    /** ContentResolver MIME, or null if unknown / unavailable. */
    fun queryMimeType(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }
    }

    fun fromUri(
        context: Context,
        uri: Uri,
        defaultName: String,
        authorEpub: String,
        authorTxt: String,
    ): Imported {
        val raw = queryDisplayName(context, uri)
        val mime = queryMimeType(context, uri)
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("null stream")
        return stream.use {
            fromStream(it, raw, defaultName, authorEpub, authorTxt, mimeType = mime)
        }
    }

    fun readText(stream: InputStream): String {
        val capacity = try {
            val avail = stream.available()
            if (avail > 0) avail else 16384
        } catch (_: Exception) {
            16384
        }
        val output = ByteArrayOutputStream(capacity)
        val buffer = ByteArray(16384)
        var count: Int
        while (stream.read(buffer).also { count = it } != -1) {
            output.write(buffer, 0, count)
        }
        val data = output.toByteArray()
        if (data.isEmpty()) return ""

        if (data.size >= 2 && (data[0].toInt() and 0xff) == 0xff && (data[1].toInt() and 0xff) == 0xfe) {
            return String(data, 2, data.size - 2, Charset.forName("UTF-16LE"))
        }
        if (data.size >= 2 && (data[0].toInt() and 0xff) == 0xfe && (data[1].toInt() and 0xff) == 0xff) {
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
        val utf8 = String(data, offset, data.size - offset, StandardCharsets.UTF_8)
        return if (utf8.indexOf('\uFFFD') >= 0) {
            String(data, offset, data.size - offset, Charset.forName("GB18030"))
        } else {
            utf8
        }
    }

    private class BoundedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Long,
    ) : InputStream() {
        private var totalRead = 0L

        override fun read(): Int {
            val b = delegate.read()
            if (b != -1) {
                totalRead++
                if (totalRead > maxBytes) {
                    throw IllegalArgumentException("file too large, max 32MB")
                }
            }
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = delegate.read(b, off, len)
            if (n > 0) {
                totalRead += n
                if (totalRead > maxBytes) {
                    throw IllegalArgumentException("file too large, max 32MB")
                }
            }
            return n
        }

        override fun close() {
            delegate.close()
        }
    }
}
