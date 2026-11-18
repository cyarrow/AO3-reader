package io.github.cyarrow.ao3reader.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import io.github.cyarrow.ao3reader.data.ReaderSettings
import io.github.cyarrow.ao3reader.reader.DisplayBlock
import io.github.cyarrow.ao3reader.reader.DisplayBlock.Type

/** Single source of truth for how each block looks, shared by layout (pagination) and rendering. */
class ReaderStyles(
    private val settings: ReaderSettings,
    private val family: FontFamily,
    private val colors: ReaderColors,
    private val density: Density,
) {
    private val size = settings.fontSize
    private val lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

    private fun base(scale: Float, color: Color, weight: Int = settings.fontWeight, lineHeight: Float = settings.lineHeight) =
        TextStyle(
            fontFamily = family,
            fontSize = (size * scale).sp,
            fontWeight = FontWeight(weight.coerceIn(100, 900)),
            lineHeight = (size * scale * lineHeight).sp,
            lineHeightStyle = lineHeightStyle,
            lineBreak = LineBreak.Simple,
            color = color,
        )

    fun style(block: DisplayBlock, isStart: Boolean): TextStyle {
        val align = when {
            block.center -> TextAlign.Center
            settings.justify -> TextAlign.Justify
            else -> TextAlign.Start
        }
        return when (block.type) {
            Type.ChapterTitle -> base(1.25f, colors.text, settings.fontWeight + 200, 1.5f).copy(textAlign = TextAlign.Center)
            Type.Label -> base(0.78f, colors.accent, settings.fontWeight + 200, 1.5f)
            Type.Meta -> base(0.86f, colors.secondary, lineHeight = (settings.lineHeight - 0.2f).coerceAtLeast(1.3f))
                .copy(textAlign = if (block.center) TextAlign.Center else TextAlign.Start)
            Type.Heading -> base(1.08f, colors.text, settings.fontWeight + 200)
                .copy(textAlign = if (block.center) TextAlign.Center else TextAlign.Start)
            Type.Quote -> base(1f, colors.secondary).copy(textAlign = align, textIndent = indent(block, isStart))
            Type.Body -> base(1f, colors.text).copy(textAlign = align, textIndent = indent(block, isStart))
            Type.Divider -> base(1f, colors.secondary)
        }
    }

    private fun indent(block: DisplayBlock, isStart: Boolean): TextIndent? =
        if (settings.indent && isStart && !block.center) TextIndent(firstLine = (size * 2).sp) else null

    fun spacingAfterPx(block: DisplayBlock): Int = with(density) {
        val em = when (block.type) {
            Type.ChapterTitle -> 1.8f
            Type.Label -> 0.35f
            Type.Meta -> 0.45f
            Type.Divider -> 0f
            else -> settings.paragraphSpacing
        }
        (size * em).sp.toPx().toInt()
    }

    val dividerHeightPx: Int = with(density) { (size * 2.2f).sp.toPx().toInt() }
}
