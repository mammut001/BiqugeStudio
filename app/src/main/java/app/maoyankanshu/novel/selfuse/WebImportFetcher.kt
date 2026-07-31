package app.maoyankanshu.novel.selfuse

import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Blocking HTTPS HTML fetch + plain-text extraction (call off the main thread).
 * Max body size matches the previous Java activity: 12 MiB.
 */
object WebImportFetcher {
    /** Public for JVM size-limit tests; same as [HttpsBodyLimits.WEB_MAX_BYTES]. */
    const val MAX_BYTES: Int = HttpsBodyLimits.WEB_MAX_BYTES

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
            // Fail fast on declared Content-Length before reading the body (API 23-safe).
            HttpsBodyLimits.rejectIfDeclaredTooLarge(
                HttpsBodyLimits.contentLengthOf(connection),
                MAX_BYTES,
            )
            val data = connection.inputStream.use { HttpsBodyLimits.readAll(it, MAX_BYTES) }
            val html = decode(data)
            val body = toText(html)
            if (body.length < 20) throw IllegalStateException("empty")
            var name = preferredTitle.trim()
            if (name.isEmpty()) name = pageTitle(html)
            if (name.isEmpty()) name = defaultTitle
            return Result(title = name, body = body, sourceUrl = cleanUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun decode(data: ByteArray): String {
        val utf8 = String(data, StandardCharsets.UTF_8)
        return if (utf8.indexOf('\uFFFD') >= 0) {
            String(data, Charset.forName("GB18030"))
        } else {
            utf8
        }
    }

    private fun pageTitle(html: String): String {
        val matcher = Pattern.compile("(?is)<title[^>]*>(.*?)</title>").matcher(html)
        return if (matcher.find()) clean(matcher.group(1).orEmpty()) else ""
    }

    private fun toText(html: String): String {
        return clean(
            html
                .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
                .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
                .replace(Regex("(?is)<(br|/p|/div|/h[1-6]|/li)[^>]*>"), "\n")
                .replace(Regex("(?s)<[^>]+>"), ""),
        )
    }

    private fun clean(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("[ \\t]*\\n[ \\t]*"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
