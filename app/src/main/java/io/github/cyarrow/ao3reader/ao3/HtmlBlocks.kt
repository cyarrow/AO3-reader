package io.github.cyarrow.ao3reader.ao3

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Converts AO3 "userstuff" HTML into a flat list of [Block]s with inline styles. */
object HtmlBlocks {
    private val blockTags = setOf(
        "p", "div", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "ul", "ol", "li",
        "center", "table", "tr", "section", "article", "pre", "details", "summary", "dl", "dt", "dd", "figure",
    )

    fun convert(root: Element): List<Block> {
        val out = mutableListOf<Block>()
        Walker(out).walkChildren(root, BlockContext())
        return out
    }

    private data class BlockContext(val center: Boolean = false, val quote: Boolean = false, val listDepth: Int = 0)

    private class Walker(val out: MutableList<Block>) {
        private val text = StringBuilder()
        private val spans = mutableListOf<StyleSpan>()
        private var ctx = BlockContext()

        fun walkChildren(el: Element, context: BlockContext) {
            for (child in el.childNodes()) walk(child, context)
            flush()
        }

        private fun walk(node: Node, context: BlockContext) {
            when (node) {
                is TextNode -> appendText(node.wholeText, context)
                is Element -> element(node, context)
            }
        }

        private fun element(el: Element, context: BlockContext) {
            val tag = el.normalName()
            if (el.hasClass("landmark") || tag == "script" || tag == "style") return
            when {
                tag == "br" -> { ctx = context; text.append('\n') }
                tag == "hr" -> { flush(); out += Block(Block.Kind.Divider) }
                tag == "img" -> {
                    val alt = el.attr("alt").ifBlank { "image" }
                    appendText("[$alt]", context)
                }
                tag.length == 2 && tag[0] == 'h' && tag[1].isDigit() -> {
                    flush()
                    val inner = convert(el).filter { it.kind == Block.Kind.Paragraph }
                    inner.forEach { out += it.copy(kind = Block.Kind.Heading) }
                }
                tag in blockTags -> {
                    flush()
                    val align = el.attr("align").lowercase()
                    val style = el.attr("style").lowercase().replace(" ", "")
                    val centered = context.center || tag == "center" || align == "center" ||
                        style.contains("text-align:center")
                    val next = context.copy(
                        center = centered,
                        quote = context.quote || tag == "blockquote",
                        listDepth = context.listDepth + if (tag == "ul" || tag == "ol") 1 else 0,
                    )
                    if (tag == "li") appendText("• ", next)
                    for (child in el.childNodes()) walk(child, next)
                    flush()
                }
                else -> {
                    val style = when (tag) {
                        "b", "strong" -> StyleSpan.Style.Bold
                        "i", "em", "cite", "var" -> StyleSpan.Style.Italic
                        "u", "ins" -> StyleSpan.Style.Underline
                        "s", "strike", "del" -> StyleSpan.Style.Strike
                        else -> null
                    }
                    val start = text.length
                    for (child in el.childNodes()) walk(child, context)
                    if (style != null && text.length > start) spans += StyleSpan(start, text.length, style)
                }
            }
        }

        private fun appendText(raw: String, context: BlockContext) {
            if (raw.isEmpty()) return
            ctx = context
            // Collapse HTML whitespace (but keep non-breaking and ideographic spaces for now).
            val collapsed = raw.replace(Regex("[ \\t\\r\\n]+"), " ")
            if (collapsed == " " && (text.isEmpty() || text.last() == '\n' || text.last() == ' ')) return
            text.append(collapsed)
        }

        /** Emits the buffered inline content, splitting on line breaks into separate paragraphs. */
        private fun flush() {
            if (text.isEmpty()) return
            val full = text.toString()
            var lineStart = 0
            while (lineStart <= full.length) {
                val nl = full.indexOf('\n', lineStart).let { if (it < 0) full.length else it }
                emitLine(full, lineStart, nl)
                lineStart = nl + 1
            }
            text.clear()
            spans.clear()
        }

        private fun emitLine(full: String, start: Int, end: Int) {
            // Trim ASCII, non-breaking and ideographic spaces; indentation is applied by the reader.
            var s = start
            var e = end
            while (s < e && isSpace(full[s])) s++
            while (e > s && isSpace(full[e - 1])) e--
            if (s >= e) return // empty spacer paragraphs (e.g. <p>&nbsp;</p>) are dropped
            val lineSpans = spans.mapNotNull { span ->
                val a = maxOf(span.start, s)
                val b = minOf(span.end, e)
                if (a < b) StyleSpan(a - s, b - s, span.style) else null
            }
            out += Block(
                kind = if (ctx.quote) Block.Kind.Quote else Block.Kind.Paragraph,
                text = full.substring(s, e),
                spans = lineSpans,
                center = ctx.center,
            )
        }

        private fun isSpace(c: Char) = c == ' ' || c == ' ' || c == '　' || c == '\t' || c == '​'
    }
}
