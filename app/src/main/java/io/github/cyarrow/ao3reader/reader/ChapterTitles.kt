package io.github.cyarrow.ao3reader.reader

/** AO3 chapter headings look like "Chapter 3: Title" or just "Chapter 3". */
data class ChapterHeading(val number: Int?, val title: String)

private val chapterPattern = Regex("""^\s*Chapter\s+(\d+)\s*(?:[:：.\-–—]\s*)?(.*)$""", RegexOption.IGNORE_CASE)

fun parseChapterHeading(raw: String): ChapterHeading {
    val m = chapterPattern.matchEntire(raw) ?: return ChapterHeading(null, raw.trim())
    return ChapterHeading(m.groupValues[1].toIntOrNull(), m.groupValues[2].trim())
}

/** "第 3 章　Title" style label for lists and headers. */
fun chapterListTitle(raw: String, format: (Int) -> String, fallback: String): String {
    val h = parseChapterHeading(raw)
    return when {
        h.number != null && h.title.isNotEmpty() -> format(h.number) + "　" + h.title
        h.number != null -> format(h.number)
        h.title.isNotEmpty() -> h.title
        else -> fallback
    }
}
