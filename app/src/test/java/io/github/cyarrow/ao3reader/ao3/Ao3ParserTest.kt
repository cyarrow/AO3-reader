package io.github.cyarrow.ao3reader.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Ao3ParserTest {
    private fun fixture(name: String) =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun parsesMultiChapterDownload() {
        val work = Ao3Parser.parseDownload(fixture("download_multi.html"), 123)
        assertEquals("测试作品", work.title)
        assertEquals(listOf("tester"), work.authors)
        assertEquals("Teen And Up Audiences", work.rating)
        assertEquals(listOf("A/B", "C & D"), work.relationships)
        assertEquals(1234, work.words)
        assertEquals("2/?", work.chaptersText)
        assertEquals("2025-11-15", work.updated)
        assertEquals(listOf("作品简介第一行", "第二行"), work.summary.map { it.text })
        assertEquals(listOf("作者的话", "换行"), work.notes.map { it.text })
        assertEquals(listOf("全文完"), work.endNotes.map { it.text })

        assertEquals(2, work.chapters.size)
        val ch1 = work.chapters[0]
        assertEquals("Chapter 1: 开端", ch1.title)
        // Spacer paragraphs are dropped, leading ideographic spaces trimmed, <br> splits paragraphs.
        assertEquals(
            listOf("“第一段。”", "第二段有斜体和粗体。", "第三段", "—【换行】", "—【再换行】", "", "置中"),
            ch1.body.map { it.text },
        )
        assertEquals(Block.Kind.Divider, ch1.body[5].kind)
        assertTrue(ch1.body[6].center)
        val styles = ch1.body[1].spans.associate { it.style to ch1.body[1].text.substring(it.start, it.end) }
        assertEquals("斜体", styles[StyleSpan.Style.Italic])
        assertEquals("粗体", styles[StyleSpan.Style.Bold])

        val ch2 = work.chapters[1]
        assertEquals(listOf("章节简介"), ch2.summary.map { it.text })
        assertEquals(listOf("章节备注"), ch2.notes.map { it.text })
        assertEquals(listOf("最后一段。"), ch2.body.map { it.text })
        assertEquals(listOf("章末备注"), ch2.endNotes.map { it.text })
    }

    @Test fun parsesSingleChapterDownload() {
        val work = Ao3Parser.parseDownload(fixture("download_single.html"), 5)
        assertEquals("One Shot", work.title)
        assertEquals(1, work.chapters.size)
        assertEquals(listOf("Only paragraph.", "Second."), work.chapters[0].body.map { it.text })
    }

    @Test fun parsesSearchResults() {
        val page = Ao3Parser.parseWorkList(fixture("search.html"), 1)
        assertTrue(page.hasNext)
        assertEquals(2, page.works.size)
        val w = page.works[0]
        assertEquals(111L, w.id)
        assertEquals("标题一", w.title)
        assertEquals(listOf("作者X"), w.authors)
        assertEquals("Explicit", w.rating)
        assertTrue(w.complete)
        assertEquals(38522, w.words)
        assertEquals("22/?", w.chapters)
        assertEquals(1024, w.kudos)
        assertEquals("简介一\n简介二", w.summary)
        assertEquals(listOf("A/B"), w.relationships)
        assertTrue(page.works[1].restricted)
        assertFalse(page.works[1].complete)
    }

    @Test fun parsesWorkPagePreview() {
        val html = fixture("work_page.html")
        val w = Ao3Parser.parseWorkPage(html, 123)
        assertTrue(w.partial)
        assertEquals("测试作品", w.title)
        assertEquals(listOf("tester"), w.authors)
        assertEquals("Explicit", w.rating)
        assertEquals(listOf("A/B"), w.relationships)
        assertEquals("3/?", w.chaptersText)
        assertEquals("2025-11-15", w.updated)
        assertEquals(listOf("简介"), w.summary.map { it.text })
        assertEquals(listOf("Chapter 1: 开端", "Chapter 2", "Chapter 3: 番外: 结局"), w.chapters.map { it.title })
        assertEquals(listOf("第一段。", "第二段。"), w.chapters[0].body.map { it.text })
        assertEquals(listOf("备注"), w.chapters[0].notes.map { it.text })
        assertTrue(w.chapters[1].body.isEmpty())
        assertEquals("https://archiveofourown.org/downloads/123/x.html?updated_at=1", Ao3Parser.parseDownloadUrl(html))
    }

    @Test fun parsesSingleChapterWorkPage() {
        val html = """<div id="workskin"><div class="preface group"><h2 class="title heading">One</h2></div>
            <div id="chapters" role="article"><h3 class="landmark heading">Work Text:</h3>
            <div class="userstuff"><p>Only.</p></div></div></div>"""
        val w = Ao3Parser.parseWorkPage(html, 1)
        assertEquals(1, w.chapters.size)
        assertEquals(listOf("Only."), w.chapters[0].body.map { it.text })
    }

    @Test fun parsesAutocomplete() {
        assertEquals(listOf("ENHYPEN (Band)", "Harry Potter - J. K. Rowling"),
            Ao3Parser.parseAutocomplete("""[{"id":"ENHYPEN (Band)","name":"ENHYPEN (Band)"},{"id":"x","name":"Harry Potter - J. K. Rowling"}]"""))
        assertEquals(emptyList<String>(), Ao3Parser.parseAutocomplete("<html>oops</html>"))
    }

    @Test fun parsesWorkIdFromInput() {
        assertEquals(59099647L, parseWorkId("https://archiveofourown.org/works/59099647/chapters/1?view_adult=true"))
        assertEquals(42L, parseWorkId(" 42 "))
        assertEquals(null, parseWorkId("hello"))
    }

    /** Runs against real downloads kept outside the repo, when provided via -PlocalFixtures. */
    @Test fun parsesLocalFixtures() {
        val dir = System.getProperty("localFixtures")?.let(::File) ?: return
        dir.listFiles { f -> f.extension == "html" }!!.forEach { file ->
            val work = Ao3Parser.parseDownload(file.readText(), 0)
            println("${file.name}: '${work.title}' by ${work.authors}, ${work.chapters.size} chapters, " +
                "${work.chapters.sumOf { c -> c.body.size }} paragraphs, chapters=${work.chaptersText}")
            work.chapters.take(3).forEach { c ->
                println("  [${c.title}] summary=${c.summary.size} notes=${c.notes.size} end=${c.endNotes.size} " +
                    "first='${c.body.firstOrNull()?.text?.take(30)}'")
            }
            assertTrue(work.title.isNotBlank())
            assertTrue(work.chapters.isNotEmpty())
            assertTrue(work.chapters.all { it.body.isNotEmpty() })
            assertTrue(work.chapters.flatMap { it.body }.none { it.kind != Block.Kind.Divider && it.text.isBlank() })
        }
    }
}
