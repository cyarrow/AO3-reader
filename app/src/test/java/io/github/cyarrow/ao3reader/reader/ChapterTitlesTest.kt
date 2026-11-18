package io.github.cyarrow.ao3reader.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTitlesTest {
    private val fmt = { n: Int -> "第 $n 章" }

    @Test fun splitsAo3Headings() {
        assertEquals(ChapterHeading(1, "一切都是基於營銷的陽謀"), parseChapterHeading("Chapter 1: 一切都是基於營銷的陽謀"))
        assertEquals(ChapterHeading(21, "番外1: 淫荡小狗"), parseChapterHeading("Chapter 21: 番外1: 淫荡小狗"))
        assertEquals(ChapterHeading(3, ""), parseChapterHeading("Chapter 3"))
        assertEquals(ChapterHeading(null, "Prologue"), parseChapterHeading("Prologue"))
    }

    @Test fun listTitles() {
        assertEquals("第 2 章　標題", chapterListTitle("Chapter 2: 標題", fmt, "W"))
        assertEquals("第 3 章", chapterListTitle("Chapter 3", fmt, "W"))
        assertEquals("W", chapterListTitle("", fmt, "W"))
    }
}
