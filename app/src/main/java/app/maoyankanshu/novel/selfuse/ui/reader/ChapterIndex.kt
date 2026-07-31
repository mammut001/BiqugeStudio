package app.maoyankanshu.novel.selfuse.ui.reader

import java.util.regex.Pattern

/** Chapter heading at a character offset in plain TXT/EPUB body text. */
data class Chapter(
    val title: String,
    val start: Int,
)

/**
 * Detect chapter headings in offline TXT/EPUB plain text (same approach as the legacy reader,
 * expanded for English Chapter/Prologue/Epilogue). Pure JVM-safe regex — minSdk 23.
 *
 * Recognized line-start headings (after optional leading spaces):
 * - Chinese: `第…章/节/回/卷` (1–18 chars between 第 and marker)
 * - English: `Chapter` + Arabic (`1`) or Roman (`IV`) numerals, e.g. `Chapter IV — The Gate`
 * - English: `Prologue` / `Epilogue` as a heading (alone, with `:`/`—` title, or Capitalized title)
 * - Chinese front/back matter: 序章、序言、楔子、前言、尾声、后记
 *
 * Not matched (false positives): mid-sentence lines like
 * `the chapter begins here` or `Prologue is mentioned in this paragraph`.
 */
object ChapterIndex {
    /**
     * Line-anchored. Latin keywords use local `(?i:…)` so Roman/title capital checks stay exact.
     * Roman digits: I V X L C D M (case-insensitive).
     */
    private val PATTERN: Pattern = Pattern.compile(
        "(?m)^\\s*(?:" +
            "第.{1,18}[章节回卷].*" +
            // Chapter 1 / Chapter IV — The Gate (not bare "chapter" in a sentence)
            "|(?i:Chapter)\\s+(?:\\d+|(?i:[IVXLCDM]+))\\b.*" +
            // Prologue/Epilogue heading only: EOL, punct title, or Capitalized title word
            // Rejects "Prologue is mentioned…" (next word is lowercase)
            "|(?i:Prologue)(?:\\s*$|\\s*[-—:.]\\s*.+|\\s+(?=\\p{Lu}).+)" +
            "|(?i:Epilogue)(?:\\s*$|\\s*[-—:.]\\s*.+|\\s+(?=\\p{Lu}).+)" +
            "|序章.*" +
            "|序言.*" +
            "|楔子.*" +
            // Require 前言 alone or 前言 + space so "前言内容" is not a heading.
            "|前言$" +
            "|前言\\s+.*" +
            "|尾声.*" +
            "|后记.*" +
            ")$",
        Pattern.UNICODE_CASE,
    )

    fun findChapters(text: String, fullTextLabel: String = "全文"): List<Chapter> {
        if (text.isEmpty()) {
            return listOf(Chapter(fullTextLabel, 0))
        }
        val chapters = ArrayList<Chapter>()
        val matcher = PATTERN.matcher(text)
        while (matcher.find()) {
            val title = matcher.group().trim()
            if (title.isEmpty()) continue
            val start = matcher.start()
            // Skip duplicate starts (overlapping / same line edge cases).
            if (chapters.isNotEmpty() && chapters[chapters.lastIndex].start == start) continue
            // Keep starts strictly increasing.
            if (chapters.isNotEmpty() && start <= chapters[chapters.lastIndex].start) continue
            chapters.add(Chapter(title, start))
        }
        if (chapters.isEmpty()) chapters.add(Chapter(fullTextLabel, 0))
        return chapters
    }

    /**
     * Index of the chapter whose [Chapter.start] is the greatest value ≤ [offset].
     * Empty list → 0; negative [offset] treated as 0.
     */
    fun chapterAtOffset(chapters: List<Chapter>, offset: Int): Int {
        if (chapters.isEmpty()) return 0
        val o = if (offset < 0) 0 else offset
        var selected = 0
        for (i in 1 until chapters.size) {
            if (chapters[i].start > o) break
            selected = i
        }
        return selected
    }
}
