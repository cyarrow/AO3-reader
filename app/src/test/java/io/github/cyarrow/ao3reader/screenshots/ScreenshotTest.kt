package io.github.cyarrow.ao3reader.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.cyarrow.ao3reader.AO3ReaderApp
import io.github.cyarrow.ao3reader.ao3.Ao3Parser
import io.github.cyarrow.ao3reader.ao3.Work
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import io.github.cyarrow.ao3reader.data.ReaderSettings
import io.github.cyarrow.ao3reader.data.ReaderTheme
import io.github.cyarrow.ao3reader.data.ReadingMode
import io.github.cyarrow.ao3reader.ui.common.WorkCard
import io.github.cyarrow.ao3reader.ui.home.HomeScreen
import io.github.cyarrow.ao3reader.ui.reader.ReaderScreen
import io.github.cyarrow.ao3reader.ui.theme.AO3ReaderTheme
import io.github.cyarrow.ao3reader.ui.work.WorkScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders key screens to PNG for visual review. Run with:
 *   ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots [-PlocalFixtures=dir]
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<AO3ReaderApp>()
    private val out = File("build/outputs/screenshots").apply { mkdirs() }
    private lateinit var work: Work

    @Before fun setUp() {
        assumeTrue(System.getProperty("screenshots") == "true")
        val local = System.getProperty("localFixtures")?.let { File(it) }?.listFiles { f -> f.extension == "html" }?.minByOrNull { it.name }
        val html = local?.readText() ?: javaClass.classLoader!!.getResource("fixtures/download_multi.html")!!.readText()
        work = Ao3Parser.parseDownload(html, 1L)
        runBlocking { app.container.library.saveWork(work) }
    }

    private fun settings(f: (ReaderSettings) -> ReaderSettings) = runBlocking { app.container.readerPrefs.update { f(ReaderSettings()) } }

    private fun reader(name: String) {
        compose.setContent { AO3ReaderTheme { ReaderScreen(app.container, 1L, onBack = {}, onOpenFonts = {}) } }
        settle()
        compose.onRoot().captureRoboImage(File(out, "$name.png").path)
    }

    /** Lets background work (dictionary loading, pagination) finish and recompose. */
    private fun settle() = repeat(25) { Thread.sleep(200); compose.waitForIdle() }

    @Test fun readerPagedPaper() { settings { it.copy(mode = ReadingMode.Paged, theme = ReaderTheme.Paper) }; reader("reader_paged_paper") }
    @Test fun readerScrollLight() { settings { it.copy(mode = ReadingMode.Scroll, theme = ReaderTheme.Light) }; reader("reader_scroll_light") }
    @Test fun readerScrollDark() { settings { it.copy(mode = ReadingMode.Scroll, theme = ReaderTheme.Dark) }; reader("reader_scroll_dark") }

    @Test fun readerChrome() {
        settings { it.copy(mode = ReadingMode.Paged, theme = ReaderTheme.Paper) }
        compose.setContent { AO3ReaderTheme { ReaderScreen(app.container, 1L, onBack = {}, onOpenFonts = {}) } }
        settle()
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "reader_chrome.png").path)
    }

    @Test fun workCards() {
        val summary = WorkSummary(
            id = 1, title = work.title, authors = work.authors, fandoms = work.fandoms, rating = work.rating,
            relationships = work.relationships, freeforms = work.freeforms,
            summary = work.summary.joinToString("\n") { it.text }, words = work.words, chapters = work.chaptersText,
            kudos = 1234, updated = work.updated,
        )
        compose.setContent {
            AO3ReaderTheme(dynamicColor = false) {
                Column { repeat(3) { WorkCard(summary) {}; HorizontalDivider() } }
            }
        }
        compose.onRoot().captureRoboImage(File(out, "work_cards.png").path)
    }

    @Test fun home() {
        app.container.library.saveProgress(1L, 3, 10, 0, 0.18f)
        compose.setContent {
            AO3ReaderTheme(dynamicColor = false) { HomeScreen(app.container.library, {}, {}, {}, {}) }
        }
        compose.onRoot().captureRoboImage(File(out, "home.png").path)
    }

    @Test fun workDetail() {
        compose.setContent { AO3ReaderTheme(dynamicColor = false) { WorkScreen(app.container, 1L, onBack = {}, onRead = {}) } }
        settle()
        compose.onRoot().captureRoboImage(File(out, "work_detail.png").path)
    }
}
