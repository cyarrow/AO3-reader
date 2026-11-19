package io.github.cyarrow.ao3reader.reader

import io.github.cyarrow.ao3reader.ao3.Block
import io.github.cyarrow.ao3reader.ao3.Chapter
import io.github.cyarrow.ao3reader.ao3.StyleSpan
import io.github.cyarrow.ao3reader.ao3.Work
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import io.github.cyarrow.ao3reader.text.ChineseConverter

/** Applies Simplified → Traditional conversion to every piece of text in a work. */
class WorkConversion(private val converter: ChineseConverter, private val taiwan: Boolean) {
    fun text(s: String) = if (s.isEmpty()) s else converter.toTraditional(s, taiwan)

    fun block(b: Block): Block {
        if (b.text.isEmpty()) return b
        if (b.spans.isEmpty()) return b.copy(text = text(b.text))
        // Convert segment by segment so style ranges stay aligned even if a phrase changes length.
        val cuts = (b.spans.flatMap { listOf(it.start, it.end) } + 0 + b.text.length).distinct().sorted()
        val out = StringBuilder()
        val newPos = HashMap<Int, Int>()
        for (i in 0 until cuts.size - 1) {
            newPos[cuts[i]] = out.length
            out.append(text(b.text.substring(cuts[i], cuts[i + 1])))
        }
        newPos[b.text.length] = out.length
        return b.copy(
            text = out.toString(),
            spans = b.spans.map { StyleSpan(newPos.getValue(it.start), newPos.getValue(it.end), it.style) },
        )
    }

    private fun blocks(list: List<Block>) = list.map(::block)

    fun work(w: Work): Work = w.copy(
        title = text(w.title),
        relationships = w.relationships.map(::text),
        characters = w.characters.map(::text),
        freeforms = w.freeforms.map(::text),
        summary = blocks(w.summary),
        notes = blocks(w.notes),
        endNotes = blocks(w.endNotes),
        chapters = w.chapters.map { c: Chapter ->
            c.copy(
                title = text(c.title),
                summary = blocks(c.summary),
                notes = blocks(c.notes),
                endNotes = blocks(c.endNotes),
                body = blocks(c.body),
            )
        },
    )

    fun summary(s: WorkSummary): WorkSummary = s.copy(
        title = text(s.title),
        summary = text(s.summary),
        relationships = s.relationships.map(::text),
        characters = s.characters.map(::text),
        freeforms = s.freeforms.map(::text),
        fandoms = s.fandoms.map(::text),
    )
}

/** Takes a text sample from the beginning of a work for script detection. */
fun Work.textSample(maxChars: Int = 3000): String = buildString {
    append(title)
    for (c in chapters) for (b in c.body) {
        append(b.text)
        if (length >= maxChars) return@buildString
    }
}
