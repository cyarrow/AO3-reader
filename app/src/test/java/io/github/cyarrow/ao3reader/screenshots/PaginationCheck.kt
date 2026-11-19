package io.github.cyarrow.ao3reader.screenshots

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import io.github.cyarrow.ao3reader.ao3.Ao3Parser
import io.github.cyarrow.ao3reader.data.ReaderSettings
import io.github.cyarrow.ao3reader.data.ReaderTheme
import io.github.cyarrow.ao3reader.reader.DisplayLabels
import io.github.cyarrow.ao3reader.reader.Paginator
import io.github.cyarrow.ao3reader.reader.buildDisplay
import io.github.cyarrow.ao3reader.ui.reader.ReaderStyles
import io.github.cyarrow.ao3reader.ui.reader.colors
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every character of a chapter must appear exactly once across its pages, in order. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class PaginationCheck {
    @get:Rule val compose = createComposeRule()

    @Test fun pagesCoverAllText() {
        val html = javaClass.classLoader!!.getResource("fixtures/download_multi.html")!!.readText()
        val local = System.getProperty("localFixtures")?.let { java.io.File(it, "work1.html") }?.takeIf { it.exists() }?.readText()
        val work = Ao3Parser.parseDownload(local ?: html, 1)
        val chapters = buildDisplay(work, true, DisplayLabels("s", "n", "e", "wn", "we"))
        compose.setContent {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val styles = ReaderStyles(ReaderSettings(), FontFamily.Serif, ReaderTheme.Paper.colors(), density)
            for (ch in chapters) {
                val pages = Paginator(measurer, 1000, 2000, styles::style, styles::spacingAfterPx, styles.dividerHeightPx).paginate(ch)
                val rebuilt = StringBuilder()
                pages.flatMap { it.slices }.forEach { rebuilt.append(ch.blocks[it.blockIndex].text.text, it.start, it.end) }
                val expected = ch.blocks.joinToString("") { it.text.text }
                println("chapter ${ch.index}: ${ch.blocks.size} blocks, ${expected.length} chars, ${pages.size} pages")
                assertEquals(expected, rebuilt.toString())
            }
        }
        compose.waitForIdle()
    }
}
