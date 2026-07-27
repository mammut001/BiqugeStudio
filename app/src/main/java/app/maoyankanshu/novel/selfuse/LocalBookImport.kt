package app.maoyankanshu.novel.selfuse

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** SAF local TXT / EPUB import helpers (call off the main thread for large files if needed). */
object LocalBookImport {
    data class Imported(
        val title: String,
        val author: String,
        val text: String,
    )

    fun fromUri(
        context: Context,
        uri: Uri,
        defaultName: String,
        authorEpub: String,
        authorTxt: String,
    ): Imported {
        val raw = uri.lastPathSegment
        var name = defaultName
        if (!raw.isNullOrEmpty()) {
            name = raw.replaceFirst(Regex("\\.[^.]+$"), "")
        }
        val epub = raw != null && raw.lowercase().endsWith(".epub")
        context.contentResolver.openInputStream(uri).use { stream ->
            if (stream == null) throw IllegalStateException("null stream")
            val content = if (epub) EpubReader.read(stream) else readText(stream)
            if (content.trim().isEmpty()) throw IllegalArgumentException("empty")
            return Imported(
                title = name,
                author = if (epub) authorEpub else authorTxt,
                text = content,
            )
        }
    }

    fun readText(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count: Int
        while (stream.read(buffer).also { count = it } != -1) {
            output.write(buffer, 0, count)
        }
        val data = output.toByteArray()
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
}
