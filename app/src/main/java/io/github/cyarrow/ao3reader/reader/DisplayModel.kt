package io.github.cyarrow.ao3reader.reader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.cyarrow.ao3reader.ao3.Block
import io.github.cyarrow.ao3reader.ao3.StyleSpan
import io.github.cyarrow.ao3reader.ao3.Work

/** A renderable unit in the reader. Both scroll and page modes render exactly these. */
data class DisplayBlock(val type: Type, val text: AnnotatedString, val center: Boolean = false) {
    enum class Type { ChapterTitle, Label, Meta, Body, Quote, Heading, Divider }
}

data class DisplayChapter(val index: Int, val title: String, val blocks: List<DisplayBlock>) {
    val charCount: Int = blocks.sumOf { it.text.length }
}

data class DisplayLabels(val summary: String, val notes: String, val endNotes: String, val workNotes: String, val workEndNotes: String)

fun Block.toAnnotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    for (s in spans) {
        val style = when (s.style) {
            StyleSpan.Style.Bold -> SpanStyle(fontWeight = FontWeight.Bold)
            StyleSpan.Style.Italic -> SpanStyle(fontStyle = FontStyle.Italic)
            StyleSpan.Style.Underline -> SpanStyle(textDecoration = TextDecoration.Underline)
            StyleSpan.Style.Strike -> SpanStyle(textDecoration = TextDecoration.LineThrough)
        }
        addStyle(style, s.start.coerceIn(0, text.length), s.end.coerceIn(0, text.length))
    }
}

private fun Block.toDisplay(meta: Boolean): DisplayBlock = when (kind) {
    Block.Kind.Divider -> DisplayBlock(DisplayBlock.Type.Divider, AnnotatedString(""))
    Block.Kind.Heading -> DisplayBlock(DisplayBlock.Type.Heading, toAnnotated(), center)
    Block.Kind.Quote -> DisplayBlock(if (meta) DisplayBlock.Type.Meta else DisplayBlock.Type.Quote, toAnnotated(), center)
    Block.Kind.Paragraph -> DisplayBlock(if (meta) DisplayBlock.Type.Meta else DisplayBlock.Type.Body, toAnnotated(), center)
}

fun buildDisplay(work: Work, showNotes: Boolean, labels: DisplayLabels): List<DisplayChapter> =
    work.chapters.mapIndexed { i, ch ->
        val blocks = mutableListOf<DisplayBlock>()
        val title = ch.title.ifBlank { work.title }
        blocks += DisplayBlock(DisplayBlock.Type.ChapterTitle, AnnotatedString(title), center = true)
        fun section(label: String, content: List<Block>) {
            if (!showNotes || content.isEmpty()) return
            blocks += DisplayBlock(DisplayBlock.Type.Label, AnnotatedString(label))
            content.forEach { blocks += it.toDisplay(meta = true) }
        }
        if (i == 0) section(labels.workNotes, work.notes)
        section(labels.summary, ch.summary)
        section(labels.notes, ch.notes)
        if (blocks.size > 1) blocks += DisplayBlock(DisplayBlock.Type.Divider, AnnotatedString(""))
        ch.body.forEach { blocks += it.toDisplay(meta = false) }
        section(labels.endNotes, ch.endNotes)
        if (i == work.chapters.lastIndex) section(labels.workEndNotes, work.endNotes)
        DisplayChapter(i, title, blocks)
    }
