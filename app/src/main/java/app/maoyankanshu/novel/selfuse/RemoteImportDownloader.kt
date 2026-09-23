package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** HTTP response failure with optional delta-seconds Retry-After guidance. */
internal class HttpStatusException(
    val statusCode: Int,
    val retryAfterSeconds: Long? = null,
) : IllegalStateException("HTTP $statusCode")

/**
 * Coalesces fast stream samples before they cross onto Compose's main thread.
 * The first sample and the completed byte count always pass; ordinary updates
 * are capped to one per [minIntervalMs]. Pure clock input keeps this testable.
 */
internal class DownloadProgressThrottle(private val minIntervalMs: Long = 250L) {
    private var hasReported = false
    private var lastBytes = -1L
    private var lastReportAtMs = 0L

    fun shouldReport(
        bytesRead: Long,
        totalBytes: Long,
        nowMs: Long,
        completed: Boolean = false,
    ): Boolean {
        if (hasReported && bytesRead == lastBytes) return false
        val atKnownEnd = totalBytes >= 0L && bytesRead >= totalBytes
        if (
            hasReported &&
            !completed &&
            !atKnownEnd &&
            nowMs - lastReportAtMs < minIntervalMs
        ) {
            return false
        }
        hasReported = true
        lastBytes = bytesRead
        lastReportAtMs = nowMs
        return true
    }
}

/** Lets UI cancellation immediately close a blocking [HttpURLConnection]. */
class RemoteImportCancellationSignal {
    private val cancelled = AtomicBoolean(false)
    private val connection = AtomicReference<HttpURLConnection?>(null)

    fun cancel() {
        cancelled.set(true)
        connection.getAndSet(null)?.disconnect()
    }

    internal fun attach(value: HttpURLConnection) {
        if (cancelled.get()) {
            value.disconnect()
            throw java.io.InterruptedIOException("download cancelled")
        }
        connection.set(value)
        if (cancelled.get() && connection.compareAndSet(value, null)) {
            value.disconnect()
            throw java.io.InterruptedIOException("download cancelled")
        }
    }

    internal fun detach(value: HttpURLConnection) {
        connection.compareAndSet(value, null)
    }
}

/** Blocking HTTPS download + TXT/EPUB decode (call off the main thread). */
object RemoteImportDownloader {
    /** Public for JVM size-limit tests; same as [HttpsBodyLimits.REMOTE_MAX_BYTES]. */
    const val MAX_BYTES: Int = HttpsBodyLimits.REMOTE_MAX_BYTES
    internal const val MAX_REDIRECTS: Int = 5

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
        cookie: String? = null,
        referer: String? = null,
        cancellationSignal: RemoteImportCancellationSignal? = null,
        fallbackContentType: String? = null,
        fallbackContentDisposition: String? = null,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): Result {
        val cleanUrl = rawUrl.trim()
        if (!cleanUrl.startsWith("https://", ignoreCase = true)) {
            throw IllegalArgumentException("HTTPS URL required")
        }
        val outcome = openFollowingHttpsRedirectsDetailed(
            initialUrl = URL(cleanUrl),
            userAgent = userAgent,
            cookie = cookie,
            referer = referer,
            cancellationSignal = cancellationSignal,
        )
        val connection = outcome.connection
        try {
            // Fail fast on declared Content-Length before reading the body (API 23-safe).
            val contentLength = HttpsBodyLimits.contentLengthOf(connection)
            HttpsBodyLimits.rejectIfDeclaredTooLarge(contentLength, MAX_BYTES)
            // Headers are available before the first body byte. Reporting here lets the UI
            // distinguish a connected download (including a useful 0%) from a stalled request.
            val progressThrottle = DownloadProgressThrottle()
            fun reportProgress(bytesRead: Long, completed: Boolean = false) {
                if (
                    onProgress != null &&
                    progressThrottle.shouldReport(
                        bytesRead = bytesRead,
                        totalBytes = contentLength,
                        nowMs = System.nanoTime() / 1_000_000L,
                        completed = completed,
                    )
                ) {
                    onProgress.invoke(bytesRead, contentLength)
                }
            }
            reportProgress(0L)
            // Prefer post-redirect URL + response headers for type/title (not the request URL).
            val finalUrl = connection.url.toString()
            val rawContentType = connection.contentType
            val rawContentDisposition = connection.getHeaderField("Content-Disposition")
            val contentType = rawContentType?.takeIf { it.isNotBlank() } ?: fallbackContentType
            val contentDisposition = rawContentDisposition?.takeIf { it.isNotBlank() } ?: fallbackContentDisposition
            val data = connection.inputStream.use { input ->
                HttpsBodyLimits.readAll(input, MAX_BYTES) { bytesRead ->
                    reportProgress(bytesRead)
                }
            }
            // A fast or unknown-length response may have had its last ordinary sample
            // coalesced. Always hand the exact completed count to the UI before decode.
            reportProgress(data.size.toLong(), completed = true)
            val epub = detectIsEpub(finalUrl, contentType, contentDisposition) ||
                LocalBookImport.isEpub(preferredTitle, contentType) ||
                looksLikeZip(data)
            if (!epub && isClearlyUnsupportedPayload(contentType, data)) {
                // Login wall after a cross-origin hop: the session cookie was deliberately
                // withheld, so the file host saw an anonymous request and returned HTML.
                // Only reachable when a non-blank cookie was supplied (in-app browser tab);
                // direct-link downloads pass no cookie and keep the generic format message.
                if (!outcome.cookieSentToFinalHost && !cookie.isNullOrBlank()) {
                    throw IllegalArgumentException("response is not TXT or EPUB (login cookie not sent cross-origin)")
                }
                throw IllegalArgumentException("response is not TXT or EPUB")
            }
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
            cancellationSignal?.detach(connection)
            connection.disconnect()
        }
    }

    /**
     * Follow a small, explicit HTTPS-only redirect chain. Browser cookies remain attached only
     * while the destination has the same origin as the URL for which they were issued.
     */
    /** Outcome of the HTTPS redirect walk: final connection + cookie provenance. */
    data class RedirectOutcome(
        val connection: HttpURLConnection,
        /** False when the final host differs from the download host (cookie withheld). */
        val cookieSentToFinalHost: Boolean,
        val finalUrl: String,
    )

    internal fun openFollowingHttpsRedirects(
        initialUrl: URL,
        userAgent: String,
        cookie: String?,
        referer: String?,
        cancellationSignal: RemoteImportCancellationSignal?,
        requestHeaders: Map<String, String> = emptyMap(),
    ): HttpURLConnection =
        openFollowingHttpsRedirectsDetailed(
            initialUrl = initialUrl,
            userAgent = userAgent,
            cookie = cookie,
            referer = referer,
            cancellationSignal = cancellationSignal,
            requestHeaders = requestHeaders,
        ).connection

    internal fun openFollowingHttpsRedirectsDetailed(
        initialUrl: URL,
        userAgent: String,
        cookie: String?,
        referer: String?,
        cancellationSignal: RemoteImportCancellationSignal?,
        requestHeaders: Map<String, String> = emptyMap(),
    ): RedirectOutcome {
        requireHttps(initialUrl)
        var currentUrl = initialUrl
        for (redirectCount in 0..MAX_REDIRECTS) {
            requireHttps(currentUrl)
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", userAgent)
                requestHeaders.forEach { (name, value) -> setRequestProperty(name, value) }
                if (sameOrigin(initialUrl, currentUrl)) {
                    cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                }
                if (redirectCount == 0) {
                    referer?.takeIf { it.startsWith("https://", ignoreCase = true) }
                        ?.let { setRequestProperty("Referer", it) }
                }
            }
            cancellationSignal?.attach(connection)
            try {
                val code = connection.responseCode
                if (code in 200..299) {
                    return RedirectOutcome(
                        connection = connection,
                        cookieSentToFinalHost = cookie.isNullOrBlank() ||
                            sameOrigin(initialUrl, currentUrl),
                        finalUrl = connection.url.toString(),
                    )
                }
                if (!isRedirectStatus(code)) {
                    throw HttpStatusException(
                        statusCode = code,
                        retryAfterSeconds = parseRetryAfterSeconds(
                            connection.getHeaderField("Retry-After"),
                            System.currentTimeMillis(),
                        ),
                    )
                }
                if (redirectCount >= MAX_REDIRECTS) {
                    throw IllegalStateException("too many redirects")
                }
                val location = connection.getHeaderField("Location")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("redirect without Location")
                val nextUrl = URL(currentUrl, location)
                requireHttps(nextUrl)
                currentUrl = nextUrl
            } catch (error: Exception) {
                cancellationSignal?.detach(connection)
                connection.disconnect()
                throw error
            }
            cancellationSignal?.detach(connection)
            connection.disconnect()
        }
        throw IllegalStateException("too many redirects")
    }

    internal fun isRedirectStatus(code: Int): Boolean =
        code == HttpURLConnection.HTTP_MOVED_PERM ||
            code == HttpURLConnection.HTTP_MOVED_TEMP ||
            code == HttpURLConnection.HTTP_SEE_OTHER ||
            code == 307 ||
            code == 308

    /** Parse Retry-After delta-seconds or the standard IMF-fixdate HTTP date. */
    internal fun parseRetryAfterSeconds(
        rawValue: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Long? {
        val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        value.toLongOrNull()?.let { return it.takeIf { seconds -> seconds >= 0L } }
        val retryAt = runCatching {
            val formatter = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                isLenient = false
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val position = ParsePosition(0)
            val parsed = formatter.parse(value, position)
            parsed?.takeIf { position.index == value.length }?.time
        }.getOrNull() ?: return null
        val remainingMs = retryAt - nowEpochMs
        if (remainingMs <= 0L) return 0L
        // Round up: retrying 100 ms early still violates the server's stated time.
        return (remainingMs + 999L) / 1_000L
    }

    internal fun sameOrigin(first: URL, second: URL): Boolean {
        return first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            effectivePort(first) == effectivePort(second)
    }

    private fun effectivePort(url: URL): Int = if (url.port >= 0) url.port else url.defaultPort

    private fun requireHttps(url: URL) {
        if (!url.protocol.equals("https", ignoreCase = true)) {
            throw IllegalArgumentException("HTTPS URL required")
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

    /** Also recognizes signed download URLs whose EPUB name only appears in the header. */
    internal fun detectIsEpub(
        finalUrl: String?,
        contentType: String?,
        contentDisposition: String?,
    ): Boolean {
        return detectIsEpub(finalUrl, contentType) ||
            LocalBookImport.isEpub(rawFilenameFromContentDisposition(contentDisposition), contentType) ||
            LocalBookImport.isEpub(rawFilenameFromUrlQuery(finalUrl), contentType)
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
        // Query-carried names only count when they look like a book file: short
        // alias keys (`name=`, `file=`) can otherwise carry `cover.jpg` or tracking
        // tokens that would shadow the real path filename.
        rawFilenameFromUrlQuery(finalUrl)
            ?.takeIf { hasBookFileExtension(it) }
            ?.let { sanitizeToTitle(it) }?.let { return it }
        parseSafeFilenameFromUrl(finalUrl)?.let { return it }
        return fallback
    }

    /** True when a query-carried filename ends in a book extension. */
    internal fun hasBookFileExtension(rawName: String?): Boolean {
        val lower = rawName?.trim()?.lowercase().orEmpty()
        return lower.endsWith(".txt") || lower.endsWith(".epub")
    }

    /**
     * File name carried by common opaque/signed download URL query parameters.
     * Covers `filename=` / `download=` plus aliases (`file=`, `name=`, `attachment=`, `fname=`,
     * `book=`, `novel=`, `down=`).
     */
    internal fun rawFilenameFromUrlQuery(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val query = runCatching { URL(url).query }.getOrNull().orEmpty()
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val separator = pair.indexOf('=')
            if (separator <= 0) continue
            val name = pair.substring(0, separator).trim().lowercase()
            if (name != "filename" && name != "download" && name != "file" &&
                name != "name" && name != "attachment" && name != "fname" &&
                name != "book" && name != "novel" && name != "down"
            ) continue
            val encoded = pair.substring(separator + 1).trim()
            if (encoded.contains("://")) continue
            val decoded = cleanAndDecodeFilenameToken(encoded)
            if (decoded.isNotEmpty() && !decoded.contains("://")) {
                return decoded
            }
        }
        return null
    }

    /** ZIP magic is enough to route a poorly-labelled EPUB through the strict EPUB parser. */
    internal fun looksLikeZip(data: ByteArray): Boolean {
        if (!hasMagic(data, 0, 0x50, 0x4B)) return false
        return hasMagic(data, 2, 0x03, 0x04) ||
            hasMagic(data, 2, 0x05, 0x06) ||
            hasMagic(data, 2, 0x07, 0x08)
    }

    /** Prevent successful HTML/API/PDF responses from becoming a garbage TXT book. */
    internal fun isClearlyUnsupportedPayload(contentType: String?, data: ByteArray): Boolean {
        val mediaType = contentType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (
            mediaType == "text/html" ||
            mediaType == "application/xhtml+xml" ||
            mediaType == "application/json" ||
            mediaType == "application/pdf" ||
            mediaType.startsWith("image/") ||
            mediaType.startsWith("audio/") ||
            mediaType.startsWith("video/")
        ) {
            return true
        }
        if (looksLikeKnownBinaryPayload(data)) {
            return true
        }
        val prefixSize = minOf(data.size, 1024)
        if (prefixSize == 0) return false
        val prefix = PlainTextDecoder.decode(data.copyOfRange(0, prefixSize))
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return prefix.startsWith("<!doctype html") ||
            prefix.startsWith("<html") ||
            prefix.startsWith("<head") ||
            prefix.startsWith("<body") ||
            (prefix.startsWith("<?xml") && prefix.contains("<html")) ||
            looksLikeApiError(prefix)
    }

    /**
     * Magic-byte fallback for download servers that label every response as octet-stream.
     * These formats cannot be a TXT/EPUB body and would otherwise become a shelf full of
     * replacement characters. ZIP is intentionally absent: it is routed through EPUB parsing.
     */
    internal fun looksLikeKnownBinaryPayload(data: ByteArray): Boolean {
        return hasMagic(data, 0, 0x25, 0x50, 0x44, 0x46, 0x2D) || // PDF
            hasMagic(data, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) || // PNG
            hasMagic(data, 0, 0xFF, 0xD8, 0xFF) || // JPEG
            hasAsciiMagic(data, 0, "GIF87a") ||
            hasAsciiMagic(data, 0, "GIF89a") ||
            (hasAsciiMagic(data, 0, "RIFF") && hasAsciiMagic(data, 8, "WEBP")) ||
            (hasAsciiMagic(data, 0, "RIFF") && hasAsciiMagic(data, 8, "WAVE")) ||
            hasAsciiMagic(data, 0, "OggS") ||
            hasAsciiMagic(data, 0, "fLaC") ||
            hasAsciiMagic(data, 0, "ID3") ||
            hasAsciiMagic(data, 4, "ftyp") || // MP4 / M4A / HEIF family
            hasMagic(data, 0, 0x1F, 0x8B) || // gzip body not decoded by a broken server
            hasAsciiMagic(data, 0, "Rar!") ||
            hasMagic(data, 0, 0x37, 0x7A, 0xBC, 0xAF, 0x27, 0x1C) || // 7z
            hasMagic(data, 0, 0x7F, 0x45, 0x4C, 0x46) || // ELF
            hasAsciiMagic(data, 0, "MZ") // PE executable
    }

    /** Conservative recognition of common JSON error envelopes returned by signed endpoints. */
    private fun looksLikeApiError(prefix: String): Boolean {
        if (!prefix.startsWith('{') && !prefix.startsWith('[')) return false
        return Regex("\"(?:error|errors|message|status|code)\"\\s*:", RegexOption.IGNORE_CASE)
            .containsMatchIn(prefix)
    }

    private fun hasAsciiMagic(data: ByteArray, offset: Int, value: String): Boolean =
        hasMagic(data, offset, *value.toByteArray(StandardCharsets.US_ASCII).map { it.toInt() and 0xFF }.toIntArray())

    private fun hasMagic(data: ByteArray, offset: Int, vararg expected: Int): Boolean {
        if (offset < 0 || data.size - offset < expected.size) return false
        return expected.indices.all { index -> (data[offset + index].toInt() and 0xFF) == expected[index] }
    }

    /**
     * Parse a display title from a Content-Disposition header value.
     * Supports `filename="…"` / `filename=…` and RFC 5987 `filename*=UTF-8''…`.
     * Returns null for missing, malformed, path-like, or injection-tainted values.
     */
    internal fun parseSafeFilenameFromContentDisposition(header: String?): String? {
        // Header-carried names only count when they look like a book file, mirroring
        // the query guard in resolveFallbackTitle: `filename="cover.jpg"` must not
        // shadow the real path filename (type detection still uses the raw token).
        val raw = rawFilenameFromContentDisposition(header)
            ?.takeIf { hasBookFileExtension(it) } ?: return null
        return sanitizeToTitle(raw)
    }

    /** Filename token with its extension retained for format detection. */
    internal fun rawFilenameFromContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        // Never process multi-line / CR-LF injection payloads as a single header value.
        if (header.indexOf('\r') >= 0 || header.indexOf('\n') >= 0) return null

        val star = FILENAME_STAR_REGEX.find(header)
        if (star != null) {
            val encoded = star.groupValues[1].trim()
            return cleanAndDecodeFilenameToken(encoded).takeIf { it.isNotBlank() }
        }

        val quoted = FILENAME_QUOTED_REGEX.find(header)
        if (quoted != null) {
            val token = cleanAndDecodeFilenameToken(quoted.groupValues[1])
            return token.takeIf { it.isNotBlank() }
        }

        val bare = FILENAME_BARE_REGEX.find(header)
        if (bare != null) {
            val token = cleanAndDecodeFilenameToken(bare.groupValues[1])
            return token.takeIf { it.isNotBlank() }
        }
        return null
    }

    /**
     * Last path segment of [url] (query/fragment stripped), sanitized to a title.
     */
    internal fun parseSafeFilenameFromUrl(url: String?): String? {
        val name = urlPathFileName(url) ?: return null
        val decoded = if (name.contains('%')) percentDecode(name) else name
        return sanitizeToTitle(decoded)
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

    internal fun cleanAndDecodeFilenameToken(rawToken: String): String {
        var token = rawToken.trim()
        if (token.length >= 2 && token.startsWith('\'') && token.endsWith('\'')) {
            token = token.substring(1, token.length - 1)
        }
        if (token.contains('%')) {
            token = percentDecode(token)
        }
        token = fixLatin1Mojibake(token)
        return token.trim()
    }

    private fun percentDecode(value: String): String {
        return try {
            val decoded = URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())
            if (decoded.contains('\uFFFD')) {
                try {
                    URLDecoder.decode(value.replace("+", "%2B"), "GB18030")
                } catch (_: Exception) {
                    decoded
                }
            } else {
                decoded
            }
        } catch (_: Exception) {
            try {
                URLDecoder.decode(value.replace("+", "%2B"), "GB18030")
            } catch (_: Exception) {
                value
            }
        }
    }

    private fun fixLatin1Mojibake(value: String): String {
        if (value.any { it.code in 0x0080..0x00FF }) {
            try {
                val bytes = value.toByteArray(StandardCharsets.ISO_8859_1)
                val candidate = String(bytes, StandardCharsets.UTF_8)
                if (!candidate.contains('\uFFFD') && candidate.length < value.length) {
                    return candidate
                }
            } catch (_: Exception) {
            }
        }
        return value
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
