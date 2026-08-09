package app.maoyankanshu.novel.selfuse

import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

/**
 * Blocking HTTPS HTML fetch + plain-text extraction (call off the main thread).
 * Max body size matches the previous Java activity: 12 MiB.
 *
 * Entity decoding is pure Kotlin (no [android.text.Html]) so it works on minSdk 23
 * and JVM unit tests: named (`&amp;` `&nbsp;` `&quot;` …), decimal (`&#160;`),
 * and hex (`&#xA0;`) entities.
 */
object WebImportFetcher {
    /** Public for JVM size-limit tests; same as [HttpsBodyLimits.WEB_MAX_BYTES]. */
    const val MAX_BYTES: Int = HttpsBodyLimits.WEB_MAX_BYTES

    private val ENTITY_PATTERN =
        Regex("""&(#x[0-9a-fA-F]+|#\d+|[A-Za-z][A-Za-z0-9]+);""")

    /** Common named entities for article text (lowercase keys). */
    private val NAMED_ENTITIES: Map<String, String> = mapOf(
        "nbsp" to " ",
        "ensp" to " ",
        "emsp" to " ",
        "thinsp" to " ",
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "mdash" to "—",
        "ndash" to "–",
        "hellip" to "…",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "laquo" to "«",
        "raquo" to "»",
        "ldquo" to "“",
        "rdquo" to "”",
        "lsquo" to "‘",
        "rsquo" to "’",
        "bull" to "•",
        "middot" to "·",
        "times" to "×",
        "divide" to "÷",
        "deg" to "°",
        "plusmn" to "±",
    )

    data class Result(
        val title: String,
        val body: String,
        val sourceUrl: String,
    )

    fun fetch(
        rawUrl: String,
        preferredTitle: String,
        userAgent: String,
        defaultTitle: String,
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
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val finalProtocol = connection.url.protocol
            if (!"https".equals(finalProtocol, ignoreCase = true)) {
                throw IllegalArgumentException("HTTPS protocol required")
            }
            // Capture the post-redirect address after verifying it is still HTTPS. The imported
            // source footer should describe the page we actually downloaded, not a stale short/
            // redirect URL the user happened to paste.
            val finalUrl = connection.url.toString()
            HttpsBodyLimits.rejectIfDeclaredTooLarge(
                HttpsBodyLimits.contentLengthOf(connection),
                MAX_BYTES,
            )
            val data = connection.inputStream.use { HttpsBodyLimits.readAll(it, MAX_BYTES) }
            // Share TXT's strict UTF-8 validation instead of treating a legitimate U+FFFD
            // character as proof that the whole page must be GB18030.
            val html = PlainTextDecoder.decode(data)
            val body = htmlToPlainText(html)
            if (body.length < 20) throw IllegalStateException("empty")
            var name = preferredTitle.trim()
            if (name.isEmpty()) name = pageTitle(html)
            if (name.isEmpty()) name = defaultTitle
            return Result(title = name, body = body, sourceUrl = finalUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun pageTitle(html: String): String {
        val matcher = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html)
        return if (matcher.find()) normalizeWhitespace(decodeHtmlEntities(matcher.group(1).orEmpty())) else ""
    }

    /**
     * Strip tags and decode entities to plain text. Internal for JVM unit tests
     * (same package) without network I/O.
     */
    internal fun htmlToPlainText(html: String): String {
        val stripped = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?is)<(br|/p|/div|/h[1-6]|/li)[^>]*>"), "\n")
            .replace(Regex("(?s)<[^>]+>"), "")
        return normalizeWhitespace(decodeHtmlEntities(stripped))
    }

    /**
     * Decode HTML character references: named, decimal (`&#NN;`), hex (`&#xHH;`).
     * Pure Kotlin — no Android HTML APIs (JVM + minSdk 23 safe).
     */
    internal fun decodeHtmlEntities(value: String): String {
        if (value.indexOf('&') < 0) return value
        return ENTITY_PATTERN.replace(value) { match ->
            val body = match.groupValues[1]
            when {
                body.startsWith("#x", ignoreCase = true) -> {
                    val code = body.substring(2).toIntOrNull(16)
                    if (code != null) codePointToString(code) else match.value
                }
                body.startsWith("#") -> {
                    val code = body.substring(1).toIntOrNull(10)
                    if (code != null) codePointToString(code) else match.value
                }
                else -> NAMED_ENTITIES[body.lowercase()] ?: match.value
            }
        }
    }

    private fun codePointToString(code: Int): String {
        if (code < 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) return ""
        when (code) {
            0xA0, 0x2002, 0x2003, 0x2009, 0x202F, 0xFEFF -> return " "
        }
        return if (code <= 0xFFFF) {
            code.toChar().toString()
        } else {
            String(Character.toChars(code))
        }
    }

    private fun normalizeWhitespace(value: String): String {
        return value
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }
}
