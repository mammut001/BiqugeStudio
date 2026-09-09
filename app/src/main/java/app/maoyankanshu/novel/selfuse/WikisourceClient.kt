package app.maoyankanshu.novel.selfuse

import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** HTTPS MediaWiki client for Chinese Wikisource search/import (off main thread). */
object WikisourceClient {
    data class Hit(val title: String, val summary: String)

    data class ImportedPage(val title: String, val author: String, val text: String)

    fun search(
        term: String,
        userAgent: String,
        cancellationSignal: RemoteImportCancellationSignal? = null,
    ): List<Hit> {
        val api =
            "https://zh.wikisource.org/w/api.php?action=query&list=search&format=json&srlimit=10&srsearch=" +
                URLEncoder.encode(term, "UTF-8")
        val response = JSONObject(readUrl(api, userAgent, cancellationSignal))
        val items = response.getJSONObject("query").getJSONArray("search")
        val found = ArrayList<Hit>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.optString("snippet", "").replace(Regex("(?s)<[^>]+>"), "")
            found.add(Hit(item.getString("title"), snippet))
        }
        return found
    }

    fun importPage(
        pageTitle: String,
        userAgent: String,
        authorLabel: String,
        cancellationSignal: RemoteImportCancellationSignal? = null,
    ): ImportedPage {
        val api =
            "https://zh.wikisource.org/w/api.php?action=parse&prop=text&format=json&page=" +
                URLEncoder.encode(pageTitle, "UTF-8")
        val parsed = JSONObject(readUrl(api, userAgent, cancellationSignal))
        val html = parsed.getJSONObject("parse").getJSONObject("text").getString("*")
        val body = html
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "")
            .replace(Regex("(?s)<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
        if (body.isEmpty()) throw IllegalStateException("empty")
        val attribution =
            "\n\n——\n来源：中文维基文库《$pageTitle》\n" +
                "链接：https://zh.wikisource.org/wiki/${pageTitle.replace(' ', '_')}\n" +
                "许可：CC BY-SA 4.0（请保留署名与许可信息）"
        return ImportedPage(title = pageTitle, author = authorLabel, text = body + attribution)
    }

    private fun readUrl(
        address: String,
        userAgent: String,
        cancellationSignal: RemoteImportCancellationSignal?,
    ): String {
        val connection = RemoteImportDownloader.openFollowingHttpsRedirects(
            initialUrl = URL(address),
            userAgent = userAgent,
            cookie = null,
            referer = null,
            cancellationSignal = cancellationSignal,
            requestHeaders = mapOf("Accept" to "application/json"),
        )
        try {
            val contentLength = HttpsBodyLimits.contentLengthOf(connection)
            HttpsBodyLimits.rejectIfDeclaredTooLarge(contentLength, HttpsBodyLimits.WEB_MAX_BYTES)
            val data = connection.inputStream.use {
                HttpsBodyLimits.readAll(it, HttpsBodyLimits.WEB_MAX_BYTES)
            }
            return String(data, StandardCharsets.UTF_8)
        } finally {
            cancellationSignal?.detach(connection)
            connection.disconnect()
        }
    }
}
