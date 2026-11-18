package io.github.cyarrow.ao3reader.ao3

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object Ao3Parser {
    private fun Element.texts(selector: String) = select(selector).map { it.text().trim() }.filter { it.isNotEmpty() }
    private fun String.toCount() = filter { it.isDigit() }.toIntOrNull() ?: 0

    // ---------------------------------------------------------------- search / listings

    fun parseWorkList(html: String, page: Int): SearchPage {
        val doc = Jsoup.parse(html, Ao3Client.BASE)
        val works = doc.select("li.work.blurb, li[role=article].blurb").mapNotNull(::parseBlurb)
        val hasNext = doc.selectFirst("ol.pagination li.next a[rel=next], ol.pagination a[rel=next]") != null
        val total = doc.selectFirst("#main h3.heading, #main h2.heading")?.text()?.trim()
        return SearchPage(works, page, hasNext, total)
    }

    private fun parseBlurb(li: Element): WorkSummary? {
        val id = li.id().removePrefix("work_").toLongOrNull()
            ?: li.selectFirst("h4.heading a[href^=/works/]")?.attr("href")
                ?.removePrefix("/works/")?.substringBefore('/')?.toLongOrNull()
            ?: return null
        val heading = li.selectFirst("h4.heading") ?: return null
        val title = heading.selectFirst("a[href^=/works/]")?.text()?.trim().orEmpty()
        val authors = heading.texts("a[rel=author]").ifEmpty {
            if (heading.text().contains("Anonymous")) listOf("Anonymous") else emptyList()
        }
        fun required(cls: String) = li.selectFirst("ul.required-tags span.$cls")?.attr("title")?.trim().orEmpty()
        val stats = li.selectFirst("dl.stats")
        fun stat(cls: String) = stats?.selectFirst("dd.$cls")?.text()?.trim().orEmpty()
        return WorkSummary(
            id = id,
            title = title,
            authors = authors,
            fandoms = li.texts("h5.fandoms a.tag"),
            rating = required("rating"),
            warnings = required("warnings").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            categories = required("category").split(",").map { it.trim() }.filter { it.isNotEmpty() },
            complete = li.selectFirst("ul.required-tags span.complete-yes") != null,
            relationships = li.texts("ul.tags li.relationships a.tag"),
            characters = li.texts("ul.tags li.characters a.tag"),
            freeforms = li.texts("ul.tags li.freeforms a.tag"),
            summary = li.select("blockquote.summary p").joinToString("\n") { it.text().trim() }
                .ifEmpty { li.selectFirst("blockquote.summary")?.text()?.trim().orEmpty() },
            language = stat("language"),
            words = stat("words").toCount(),
            chapters = stat("chapters"),
            kudos = stat("kudos").toCount(),
            comments = stat("comments").toCount(),
            bookmarks = stat("bookmarks").toCount(),
            hits = stat("hits").toCount(),
            updated = li.selectFirst("p.datetime")?.text()?.trim().orEmpty(),
            restricted = li.selectFirst("img[title=Restricted], .restricted") != null,
        )
    }

    /** Parses `[{"id": "...", "name": "..."}]` from AO3's autocomplete endpoints. */
    fun parseAutocomplete(json: String): List<String> = runCatching {
        kotlinx.serialization.json.Json.parseToJsonElement(json).let { it as kotlinx.serialization.json.JsonArray }
            .mapNotNull { (it as? kotlinx.serialization.json.JsonObject)?.get("name")?.let { n -> (n as kotlinx.serialization.json.JsonPrimitive).content } }
    }.getOrDefault(emptyList())

    // ---------------------------------------------------------------- work page

    /** Finds the HTML download link on a work page, or throws if the page is a login wall. */
    fun parseDownloadUrl(html: String): String {
        val doc = Jsoup.parse(html, Ao3Client.BASE)
        checkLoginWall(doc)
        val link = doc.select("li.download a[href], a[href*=/downloads/]")
            .map { it.attr("abs:href") }
            .firstOrNull { it.substringBefore('?').endsWith(".html") }
            ?: throw Ao3Exception.Parse("No download link")
        return link
    }

    private fun checkLoginWall(doc: Document) {
        val loginForm = doc.selectFirst("form#new_user, form#loginform, #signin") != null &&
            doc.selectFirst("#workskin, #chapters") == null
        if (loginForm || doc.location().contains("/users/login")) throw Ao3Exception.NeedsLogin()
    }

    // ---------------------------------------------------------------- downloaded HTML (full work)

    fun parseDownload(html: String, workId: Long): Work {
        val doc = Jsoup.parse(html, Ao3Client.BASE)
        val preface = doc.selectFirst("#preface") ?: throw Ao3Exception.Parse("Missing preface")
        val meta = preface.selectFirst("div.meta") ?: preface

        val tags = mutableMapOf<String, Element>()
        meta.select("dl.tags > dt").forEach { dt ->
            val dd = dt.nextElementSibling()
            if (dd != null && dd.normalName() == "dd") tags[dt.text().trim().removeSuffix(":").lowercase()] = dd
        }
        fun tagList(vararg keys: String) =
            keys.firstNotNullOfOrNull { tags[it] }?.select("a")?.map { it.text().trim() }.orEmpty()
        val statsText = tags["stats"]?.text().orEmpty()
        fun statValue(name: String) = Regex("$name:\\s*([^\\s]+)").find(statsText)?.groupValues?.get(1).orEmpty()

        val (summary, notes) = labelledBlockquotes(meta)
        val afterword = doc.selectFirst("#afterword")
        val endNotes = afterword?.let { labelledBlockquotes(it).second }.orEmpty()

        return Work(
            id = workId,
            title = meta.selectFirst("h1")?.text()?.trim()
                ?: preface.selectFirst("p.message b")?.text()?.trim().orEmpty(),
            authors = meta.select(".byline a[rel=author]").map { it.text().trim() }
                .ifEmpty { listOfNotNull(meta.selectFirst(".byline")?.text()?.removePrefix("by")?.trim()) },
            fandoms = tagList("fandom", "fandoms"),
            rating = tagList("rating", "ratings").firstOrNull().orEmpty(),
            warnings = tagList("archive warning", "archive warnings"),
            relationships = tagList("relationship", "relationships"),
            characters = tagList("character", "characters"),
            freeforms = tagList("additional tags", "additional tag"),
            language = tags["language"]?.text()?.trim().orEmpty(),
            words = statValue("Words").filter { it.isDigit() }.toIntOrNull() ?: 0,
            chaptersText = statValue("Chapters"),
            published = statValue("Published"),
            updated = statValue("Updated").ifEmpty { statValue("Completed") }.ifEmpty { statValue("Published") },
            summary = summary,
            notes = notes,
            endNotes = endNotes,
            chapters = parseChapters(doc),
        )
    }

    /** Returns (summary, notes) from "<p>Summary</p><blockquote>" style label pairs. */
    private fun labelledBlockquotes(container: Element): Pair<List<Block>, List<Block>> {
        var summary = emptyList<Block>()
        val notes = mutableListOf<Block>()
        container.select("blockquote.userstuff").forEach { bq ->
            val label = bq.previousElementSibling()?.text()?.trim()?.lowercase().orEmpty()
            val blocks = HtmlBlocks.convert(bq).map { if (it.kind == Block.Kind.Quote) it.copy(kind = Block.Kind.Paragraph) else it }
            when {
                label.contains("summary") -> summary = blocks
                label.contains("notes") -> notes += blocks
            }
        }
        return summary to notes
    }

    private fun parseChapters(doc: Document): List<Chapter> {
        val container = doc.selectFirst("#chapters") ?: throw Ao3Exception.Parse("Missing chapters")
        val children = container.children()
        if (children.none { it.hasClass("meta") }) {
            // Single-chapter work: the container itself is the text.
            return listOf(Chapter(index = 0, title = "", body = HtmlBlocks.convert(container)))
        }
        val chapters = mutableListOf<Chapter>()
        var title = ""
        var summary = emptyList<Block>()
        var notes = emptyList<Block>()
        for (child in children) {
            when {
                child.hasClass("meta") -> {
                    val heading = child.selectFirst("h2.heading")?.text()?.trim()
                    val (s, n) = labelledBlockquotes(child)
                    if (heading == null && chapters.isNotEmpty()) {
                        // Chapter end notes follow the chapter text in their own meta block.
                        val last = chapters.removeAt(chapters.lastIndex)
                        chapters += last.copy(endNotes = last.endNotes + n)
                    } else {
                        title = heading.orEmpty()
                        summary = s
                        notes = n
                    }
                }
                child.hasClass("userstuff") -> {
                    chapters += Chapter(
                        index = chapters.size,
                        title = title,
                        summary = summary,
                        notes = notes,
                        body = HtmlBlocks.convert(child),
                    )
                    title = ""
                    summary = emptyList()
                    notes = emptyList()
                }
            }
        }
        return chapters
    }
}
