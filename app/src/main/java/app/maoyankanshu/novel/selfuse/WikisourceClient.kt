package app.maoyankanshu.novel.selfuse

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** HTTPS MediaWiki client for Chinese Wikisource search/import (off main thread). */
object WikisourceClient {
    data class Hit(val title: String, val summary: String)

    data class ImportedPage(val title: String, val author: String, val text: String)

    fun search(term: String, userAgent: String): List<Hit> {
        val api =
            "https://zh.wikisource.org/w/api.php?action=query&list=search&format=json&srlimit=10&srsearch=" +
                URLEncoder.encode(term, "UTF-8")
        val response = JSONObject(readUrl(api, userAgent))
        val items = response.getJSONObject("query").getJSONArray("search")
        val found = ArrayList<Hit>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val snippet = item.optString("snippet", "").replace(Regex("(?s)<[^>]+>"), "")
            found.add(Hit(item.getString("title"), snippet))
        }
        return found
    }

    fun importPage(pageTitle: String, userAgent: String, authorLabel: String): ImportedPage {
        val api =
            "https://zh.wikisource.org/w/api.php?action=parse&prop=text&format=json&page=" +
                URLEncoder.encode(pageTitle, "UTF-8")
        val parsed = JSONObject(readUrl(api, userAgent))
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

    private fun readUrl(address: String, userAgent: String): String {
        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            connection.inputStream.use { return readAllAsString(it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readAllAsString(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count: Int
        while (stream.read(buffer).also { count = it } != -1) {
            output.write(buffer, 0, count)
        }
        return String(output.toByteArray(), StandardCharsets.UTF_8)
    }
}
