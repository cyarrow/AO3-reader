package io.github.cyarrow.ao3reader.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints

/** A run of lines from one block, [start, end) in that block's text. */
data class Slice(val blockIndex: Int, val start: Int, val end: Int)

data class Page(val slices: List<Slice>) {
    val first: Slice get() = slices.first()
}

/**
 * Splits a chapter into pages by laying out each block once and distributing its lines.
 * Rendering uses the same styles with greedy line breaking, so each slice re-wraps identically.
 */
class Paginator(
    private val measurer: TextMeasurer,
    private val widthPx: Int,
    private val heightPx: Int,
    private val styleFor: (DisplayBlock, Boolean) -> TextStyle,
    private val spacingAfterPx: (DisplayBlock) -> Int,
    private val dividerHeightPx: Int,
) {
    fun paginate(chapter: DisplayChapter): List<Page> {
        val pages = mutableListOf<Page>()
        var current = mutableListOf<Slice>()
        var used = 0f

        fun newPage() {
            if (current.isNotEmpty()) pages += Page(current)
            current = mutableListOf()
            used = 0f
        }

        chapter.blocks.forEachIndexed { index, block ->
            val gap = if (current.isEmpty()) 0 else spacingAfterPx(chapter.blocks[current.last().blockIndex])
            if (block.type == DisplayBlock.Type.Divider) {
                if (used + gap + dividerHeightPx > heightPx) newPage()
                used += (if (current.isEmpty()) 0 else gap) + dividerHeightPx
                current += Slice(index, 0, 0)
                return@forEachIndexed
            }
            // A chapter opening always starts on a fresh page.
            if (block.type == DisplayBlock.Type.ChapterNumber ||
                (block.type == DisplayBlock.Type.ChapterTitle && index == 0)) newPage()
            val layout = measurer.measure(
                block.text,
                style = styleFor(block, true),
                constraints = Constraints(maxWidth = widthPx),
            )
            var sliceStart = 0
            var first = true
            for (line in 0 until layout.lineCount) {
                val lineHeight = layout.getLineBottom(line) - layout.getLineTop(line)
                val extra = if (first && current.isNotEmpty()) gap.toFloat() else 0f
                if (used + extra + lineHeight > heightPx && current.isNotEmpty()) {
                    // Close the slice so far (if any) on this page and continue on the next.
                    val lineStart = layout.getLineStart(line)
                    if (lineStart > sliceStart) current += Slice(index, sliceStart, lineStart)
                    sliceStart = lineStart
                    newPage()
                    first = false
                    used += lineHeight
                } else {
                    used += extra + lineHeight
                    first = false
                }
            }
            current += Slice(index, sliceStart, block.text.length)
        }
        newPage()
        if (pages.isEmpty()) pages += Page(listOf(Slice(0, 0, 0)))
        return pages
    }
}

/** Index of the page containing the given block position. */
fun List<Page>.indexOfPosition(blockIndex: Int, charOffset: Int): Int {
    for ((i, page) in withIndex()) {
        val last = page.slices.last()
        if (last.blockIndex > blockIndex || (last.blockIndex == blockIndex && (charOffset < last.end || last.end == last.start))) return i
    }
    return lastIndex.coerceAtLeast(0)
}
