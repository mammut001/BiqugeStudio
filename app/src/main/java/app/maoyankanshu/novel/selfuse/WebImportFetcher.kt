package app.maoyankanshu.novel.selfuse

import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    private const val MAX_BYTES = 12 * 1024 * 1024

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
        val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val data = connection.inputStream.use { readAll(it) }
            val html = decode(data)
            val body = toText(html)
            if (body.length < 20) throw IllegalStateException("empty")
            var name = preferredTitle.trim()
            if (name.isEmpty()) name = pageTitle(html)
            if (name.isEmpty()) name = defaultTitle
            return Result(title = name, body = body, sourceUrl = rawUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAll(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var n: Int
        while (input.read(buffer).also { n = it } != -1) {
            if (out.size() + n > MAX_BYTES) throw IllegalStateException("too large")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
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
