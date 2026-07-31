package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Blocking HTTPS download + TXT/EPUB decode (call off the main thread). */
object RemoteImportDownloader {
    /** Public for JVM size-limit tests; same as [HttpsBodyLimits.REMOTE_MAX_BYTES]. */
    const val MAX_BYTES: Int = HttpsBodyLimits.REMOTE_MAX_BYTES

    data class Result(
        val title: String,
        val author: String,
        val text: String,
        val isEpub: Boolean,
    )

    fun download(
        rawUrl: String,
        preferredTitle: String,
        userAgent: String,
        defaultEpubTitle: String,
        defaultTxtTitle: String,
        authorEpub: String,
        authorTxt: String,
    ): Result {
        val cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("HTTPS URL required")
        }
        val connection = (URL(cleanUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val finalProtocol = connection.url.protocol
            if (!"https".equals(finalProtocol, ignoreCase = true)) {
                throw IllegalArgumentException("HTTPS protocol required")
            }
            // Fail fast on declared Content-Length before reading the body (API 23-safe).
            HttpsBodyLimits.rejectIfDeclaredTooLarge(
                HttpsBodyLimits.contentLengthOf(connection),
                MAX_BYTES,
            )
            val contentType = connection.contentType
            val data = connection.inputStream.use { HttpsBodyLimits.readAll(it, MAX_BYTES) }
            val epub = cleanUrl.lowercase().contains(".epub") ||
                (contentType != null && contentType.contains("epub"))
            val text = if (epub) EpubReader.read(ByteArrayInputStream(data)) else decodeText(data)
            if (text.trim().isEmpty()) throw IllegalStateException("empty")
            var bookTitle = preferredTitle.trim()
            if (bookTitle.isEmpty()) {
                bookTitle = fileName(cleanUrl, if (epub) defaultEpubTitle else defaultTxtTitle)
            }
            return Result(
                title = bookTitle,
                author = if (epub) authorEpub else authorTxt,
                text = text,
                isEpub = epub,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeText(data: ByteArray): String {
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

    private fun fileName(url: String, fallback: String): String {
        val name = url.substringAfterLast('/')
            .replace(Regex("[?].*$"), "")
            .replace(Regex("\\.[^.]+$"), "")
        return name.ifEmpty { fallback }
    }
}
