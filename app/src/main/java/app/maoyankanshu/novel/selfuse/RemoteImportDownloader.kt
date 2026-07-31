package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
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
        val coverBytes: ByteArray? = null,
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
            // Prefer post-redirect URL + response headers for type/title (not the request URL).
            val finalUrl = connection.url.toString()
            val contentType = connection.contentType
            val contentDisposition = connection.getHeaderField("Content-Disposition")
            val data = connection.inputStream.use { HttpsBodyLimits.readAll(it, MAX_BYTES) }
            val epub = detectIsEpub(finalUrl, contentType)
            var bookTitle = preferredTitle.trim()
            var author = if (epub) authorEpub else authorTxt
            var coverBytes: ByteArray? = null
            val text: String
            if (epub) {
                val book = EpubReader.readBook(ByteArrayInputStream(data))
                text = book.text
                if (bookTitle.isEmpty()) {
                    // OPF dc:title still wins over Content-Disposition / URL filename.
                    val embedded = book.title?.trim().orEmpty()
                    bookTitle = embedded.ifEmpty {
                        resolveFallbackTitle(contentDisposition, finalUrl, defaultEpubTitle)
                    }
                }
                val embeddedAuthor = book.author?.trim().orEmpty()
                if (embeddedAuthor.isNotEmpty()) author = embeddedAuthor
                coverBytes = book.coverImage
            } else {
                text = decodeText(data)
                if (bookTitle.isEmpty()) {
                    bookTitle = resolveFallbackTitle(contentDisposition, finalUrl, defaultTxtTitle)
                }
            }
            if (text.trim().isEmpty()) throw IllegalStateException("empty")
            return Result(
                title = bookTitle,
                author = author,
                text = text,
                isEpub = epub,
                coverBytes = coverBytes,
            )
        } finally {
            connection.disconnect()
        }
    }

    /**
     * EPUB when the **final** URL path ends with `.epub` (any case) or Content-Type is
     * `application/epub+zip` (case-insensitive; `;…` parameters ignored).
     * Pure for JVM unit tests — no network.
     */
    internal fun detectIsEpub(finalUrl: String?, contentType: String?): Boolean {
        val pathName = urlPathFileName(finalUrl)
        return LocalBookImport.isEpub(pathName, contentType)
    }

    /**
     * Title when the user left preferredTitle blank:
     * 1) safe filename from Content-Disposition (if present and clean),
     * 2) else safe filename from final URL path,
     * 3) else [fallback].
     * Pure for JVM unit tests — no network.
     */
    internal fun resolveFallbackTitle(
        contentDisposition: String?,
        finalUrl: String?,
        fallback: String,
    ): String {
        parseSafeFilenameFromContentDisposition(contentDisposition)?.let { return it }
        parseSafeFilenameFromUrl(finalUrl)?.let { return it }
        return fallback
    }

    /**
     * Parse a display title from a Content-Disposition header value.
     * Supports `filename="…"` / `filename=…` and RFC 5987 `filename*=UTF-8''…`.
     * Returns null for missing, malformed, path-like, or injection-tainted values.
     */
    internal fun parseSafeFilenameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // Never process multi-line / CR-LF injection payloads as a single header value.
        if (header.indexOf('\r') >= 0 || header.indexOf('\n') >= 0) return null

        val star = FILENAME_STAR_REGEX.find(header)
        if (star != null) {
            val encoded = star.groupValues[1].trim()
            sanitizeToTitle(percentDecode(encoded))?.let { return it }
        }

        val quoted = FILENAME_QUOTED_REGEX.find(header)
        if (quoted != null) {
            sanitizeToTitle(quoted.groupValues[1])?.let { return it }
        }

        val bare = FILENAME_BARE_REGEX.find(header)
        if (bare != null) {
            var token = bare.groupValues[1].trim()
            // Strip optional surrounding single quotes some servers emit.
            if (token.length >= 2 && token.startsWith('\'') && token.endsWith('\'')) {
                token = token.substring(1, token.length - 1)
            }
            sanitizeToTitle(token)?.let { return it }
        }
        return null
    }

    /**
     * Last path segment of [url] (query/fragment stripped), sanitized to a title.
     */
    internal fun parseSafeFilenameFromUrl(url: String?): String? {
        val name = urlPathFileName(url) ?: return null
        return sanitizeToTitle(name)
    }

    /** Last path segment without query/fragment; may still include an extension. */
    internal fun urlPathFileName(url: String?): String? {
        if (url.isNullOrBlank()) return null
        var path = url.trim()
        path = path.substringBefore('#').substringBefore('?')
        // Drop scheme://host for full URLs so we never treat host as a filename.
        val schemeSep = path.indexOf("://")
        if (schemeSep >= 0) {
            path = path.substring(schemeSep + 3)
            val slash = path.indexOf('/')
            path = if (slash >= 0) path.substring(slash + 1) else ""
        }
        if (path.isEmpty()) return null
        // Normalize Windows separators that can appear in odd redirect targets.
        val segment = path.replace('\\', '/').substringAfterLast('/')
        return segment.ifEmpty { null }
    }

    /**
     * Turn a raw filename token into a safe book title: basename only, no extension,
     * no control characters, no path separators. Null if unusable.
     */
    internal fun sanitizeToTitle(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        var name = raw.trim()
        // Reject header-injection / binary control characters early.
        if (name.any { ch -> ch.code < 0x20 || ch == '\u007F' }) return null
        name = name.replace('\\', '/')
        // Never keep directory components or traversal as the displayed title.
        name = name.substringAfterLast('/')
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.contains('/') || name.contains('\\')) return null
        // Drop a single trailing extension (.epub, .txt, .EPUB, …).
        name = name.replace(Regex("\\.[^.]+$"), "")
        name = name.trim()
        if (name.isEmpty() || name == "." || name == "..") return null
        if (name.any { ch -> ch.code < 0x20 || ch == '\u007F' }) return null
        return name
    }

    private fun percentDecode(value: String): String {
        return try {
            URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            value
        }
    }

    /**
     * Plain TXT body decode (BOM + UTF-8/UTF-16/UTF-32/GB18030).
     * Internal for JVM encoding tests — no network.
     */
    internal fun decodeText(data: ByteArray): String = PlainTextDecoder.decode(data)

    private val FILENAME_STAR_REGEX =
        Regex("""filename\*\s*=\s*(?:UTF-8|utf-8)''([^;\s]+)""", RegexOption.IGNORE_CASE)

    private val FILENAME_QUOTED_REGEX =
        Regex("""(?i)filename\s*=\s*"([^"]*)"""")

    /** Unquoted token only — do not re-parse `filename=""`. */
    private val FILENAME_BARE_REGEX =
        Regex("""(?i)filename\s*=\s*(?!")([^;\s]+)""")
}
