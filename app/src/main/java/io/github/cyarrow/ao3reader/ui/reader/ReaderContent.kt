package io.github.cyarrow.ao3reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.reader.DisplayBlock
import io.github.cyarrow.ao3reader.reader.DisplayChapter
import io.github.cyarrow.ao3reader.reader.Page

@Composable
fun BlockView(block: DisplayBlock, styles: ReaderStyles, colors: ReaderColors, start: Int = 0, end: Int = block.text.length) {
    if (block.type == DisplayBlock.Type.Divider) {
        val h = with(LocalDensity.current) { styles.dividerHeightPx.toDp() }
        Box(Modifier.fillMaxWidth().height(h), contentAlignment = Alignment.Center) {
            Box(Modifier.width(48.dp).height(1.dp).background(colors.secondary.copy(alpha = 0.5f)))
        }
        return
    }
    Text(
        text = if (start == 0 && end == block.text.length) block.text else block.text.subSequence(start, end),
        style = styles.style(block, isStart = start == 0),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Renders one page: slices separated by exactly the spacing the paginator assumed. */
@Composable
fun PageView(chapter: DisplayChapter, page: Page, styles: ReaderStyles, colors: ReaderColors) {
    val density = LocalDensity.current
    Column(Modifier.fillMaxWidth()) {
        page.slices.forEachIndexed { i, slice ->
            if (i > 0) {
                val prev = chapter.blocks[page.slices[i - 1].blockIndex]
                val gap = with(density) { styles.spacingAfterPx(prev).toDp() }
                Box(Modifier.height(gap))
            }
            val block = chapter.blocks[slice.blockIndex]
            if (block.type == DisplayBlock.Type.Divider || slice.end > slice.start) {
                BlockView(block, styles, colors, slice.start, slice.end)
            }
        }
    }
}

@Composable
fun ScrollBlock(block: DisplayBlock, styles: ReaderStyles, colors: ReaderColors) {
    val gap = with(LocalDensity.current) { styles.spacingAfterPx(block).toDp() }
    Box(Modifier.padding(bottom = gap)) { BlockView(block, styles, colors) }
}
