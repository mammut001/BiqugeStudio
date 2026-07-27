package app.maoyankanshu.novel.selfuse.ui.reader

import java.util.regex.Pattern

/** Same chapter heading pattern as the legacy Java reader. */
data class Chapter(
    val title: String,
    val start: Int,
)

object ChapterIndex {
    private val PATTERN: Pattern =
        Pattern.compile("(?m)^\\s*第.{1,18}[章节回].*$", Pattern.UNICODE_CASE)

    fun findChapters(text: String, fullTextLabel: String = "全文"): List<Chapter> {
        val chapters = ArrayList<Chapter>()
        val matcher = PATTERN.matcher(text)
        while (matcher.find()) {
            chapters.add(Chapter(matcher.group().trim(), matcher.start()))
        }
        if (chapters.isEmpty()) chapters.add(Chapter(fullTextLabel, 0))
        return chapters
    }

    fun chapterAtOffset(chapters: List<Chapter>, offset: Int): Int {
        if (chapters.isEmpty()) return 0
        var selected = 0
        for (i in 1 until chapters.size) {
            if (chapters[i].start > offset) break
            selected = i
        }
        return selected
    }
}
