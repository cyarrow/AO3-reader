package io.github.cyarrow.ao3reader.screenshots

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.cyarrow.ao3reader.AO3ReaderApp
import io.github.cyarrow.ao3reader.MainActivity
import io.github.cyarrow.ao3reader.ao3.Ao3Parser
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

/** Whole-app screenshots including the bottom navigation bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rTW-w411dp-h891dp-xxhdpi")
class AppScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val out = File("build/outputs/screenshots").apply { mkdirs() }

    @Before fun setUp() {
        assumeTrue(System.getProperty("screenshots") == "true")
        val app = compose.activity.application as AO3ReaderApp
        val html = javaClass.classLoader!!.getResource("fixtures/download_multi.html")!!.readText()
        runBlocking {
            app.container.library.saveWork(Ao3Parser.parseDownload(html, 1L))
            app.container.library.saveWork(Ao3Parser.parseDownload(html, 2L).copy(title = "另一部作品", chaptersText = "5/5"))
        }
        runBlocking {
            listOf(3L to "春日序曲", 4L to "Summer Rain", 5L to "雪夜長談").forEach { (id, t) ->
                app.container.library.saveWork(Ao3Parser.parseDownload(html, id).copy(title = t, fandoms = listOf("RIIZE (Band)")))
            }
        }
        app.container.library.saveProgress(1L, 1, 3, 0, 0.42f)
        app.container.library.saveProgress(3L, 0, 3, 0, 0.12f)
        app.container.library.saveProgress(4L, 1, 3, 0, 0.77f)
        app.container.library.update(2L) { it?.copy(remoteChapters = "7/?") }
    }

    private fun settle() = repeat(10) { Thread.sleep(150); compose.waitForIdle() }

    @Test fun homeAndShelf() {
        settle()
        captureScreenRoboImage(File(out, "app_home.png").path)
        compose.onNodeWithText("書架").performClick()
        settle()
        captureScreenRoboImage(File(out, "app_shelf.png").path)
        compose.onNodeWithText("Summer Rain", substring = true).performTouchInput { longClick() }
        compose.onNodeWithText("春日序曲", substring = true).performClick()
        settle()
        captureScreenRoboImage(File(out, "app_shelf_select.png").path)
    }
}
