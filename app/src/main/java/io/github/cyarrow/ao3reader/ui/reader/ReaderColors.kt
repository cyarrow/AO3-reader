package io.github.cyarrow.ao3reader.ui.reader

import androidx.compose.ui.graphics.Color
import io.github.cyarrow.ao3reader.data.ReaderTheme

data class ReaderColors(val background: Color, val text: Color, val secondary: Color, val accent: Color, val isDark: Boolean)

fun ReaderTheme.colors(): ReaderColors = when (this) {
    ReaderTheme.Light -> ReaderColors(Color(0xFFFCFCFA), Color(0xFF242424), Color(0xFF6E6E6E), Color(0xFF990000), false)
    ReaderTheme.Paper -> ReaderColors(Color(0xFFF4ECD8), Color(0xFF3A3024), Color(0xFF7A6A55), Color(0xFF8B3A2B), false)
    ReaderTheme.Green -> ReaderColors(Color(0xFFDCEBDD), Color(0xFF26342A), Color(0xFF5C6E60), Color(0xFF2E6B3F), false)
    ReaderTheme.Dark -> ReaderColors(Color(0xFF1C1B1A), Color(0xFFCFCAC2), Color(0xFF8A857E), Color(0xFFE59A8C), true)
    ReaderTheme.Black -> ReaderColors(Color(0xFF000000), Color(0xFFA8A49E), Color(0xFF6B6863), Color(0xFFC77D70), true)
}
