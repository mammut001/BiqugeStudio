package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

/**
 * Shared size guards for HTTPS import bodies.
 * Declared [Content-Length] is checked before streaming; streams are still capped
 * when the header is missing or wrong. Pure helpers are unit-testable on the JVM.
 */
internal object HttpsBodyLimits {
    /** Remote TXT/EPUB direct download (matches [RemoteImportDownloader]). */
    const val REMOTE_MAX_BYTES: Int = 50 * 1024 * 1024

    /** HTML article fetch (matches [WebImportFetcher]). */
    const val WEB_MAX_BYTES: Int = 12 * 1024 * 1024

    /**
     * Parse response size. Prefer the Content-Length header (works for values
     * beyond [Int.MAX_VALUE] and avoids API 24 [HttpURLConnection.getContentLengthLong]).
     * Returns -1 when unknown.
     */
    fun parseContentLength(headerValue: String?, contentLengthField: Int): Long {
        val fromHeader = headerValue?.trim()?.toLongOrNull()
        if (fromHeader != null && fromHeader >= 0L) return fromHeader
        return if (contentLengthField >= 0) contentLengthField.toLong() else -1L
    }

    fun contentLengthOf(connection: HttpURLConnection): Long =
        parseContentLength(
            connection.getHeaderField("Content-Length"),
            connection.contentLength,
        )

    /**
     * Fail fast when the server declares a body larger than [maxBytes].
     * Unknown length (-1) is allowed; streaming still enforces the cap.
     */
    fun rejectIfDeclaredTooLarge(contentLength: Long, maxBytes: Int) {
        if (contentLength > maxBytes.toLong()) {
            throw IllegalStateException("too large")
        }
    }

    fun readAll(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var n: Int
        while (input.read(buffer).also { n = it } != -1) {
            if (out.size().toLong() + n > maxBytes.toLong()) {
                throw IllegalStateException("too large")
            }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
