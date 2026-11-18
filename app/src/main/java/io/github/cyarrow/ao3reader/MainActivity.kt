package io.github.cyarrow.ao3reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.cyarrow.ao3reader.ao3.parseWorkId
import io.github.cyarrow.ao3reader.ui.fonts.FontsScreen
import io.github.cyarrow.ao3reader.ui.home.HomeScreen
import io.github.cyarrow.ao3reader.ui.reader.ReaderScreen
import io.github.cyarrow.ao3reader.ui.search.SearchScreen
import io.github.cyarrow.ao3reader.ui.settings.SettingsScreen
import io.github.cyarrow.ao3reader.ui.theme.AO3ReaderTheme
import io.github.cyarrow.ao3reader.ui.update.UpdateDialog
import io.github.cyarrow.ao3reader.ui.work.WorkScreen
import io.github.cyarrow.ao3reader.update.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    /** Set by the reader to turn pages with the volume keys. */
    var keyHandler: ((KeyEvent) -> Boolean)? = null

    /** Work ids from AO3 links opened with this app. */
    private val incomingWork = MutableStateFlow<Long?>(null)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        keyHandler?.invoke(event) == true || super.dispatchKeyEvent(event)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val text = intent?.dataString ?: intent?.getStringExtra(Intent.EXTRA_TEXT) ?: return
        parseWorkId(text)?.let { incomingWork.value = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AO3ReaderApp).container
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            AO3ReaderTheme {
                val nav = rememberNavController()
                var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var autoChecked by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!autoChecked && container.settings.autoCheckUpdates.first()) {
                        autoChecked = true
                        container.updateManager.autoCheck()?.let { pendingUpdate = it }
                    }
                }
                LaunchedEffect(Unit) {
                    incomingWork.collect { id ->
                        if (id != null) {
                            nav.navigate("work/$id")
                            incomingWork.value = null
                        }
                    }
                }

                NavHost(nav, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            library = container.library,
                            onOpenSettings = { nav.navigate("settings") },
                            onOpenSearch = { nav.navigate("search") },
                            onOpenWork = { nav.navigate("work/$it") },
                            onRead = { nav.navigate("reader/$it") },
                        )
                    }
                    composable("search") {
                        SearchScreen(container, onBack = { nav.popBackStack() }, onOpenWork = { nav.navigate("work/$it") })
                    }
                    composable("work/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments!!.getLong("id")
                        WorkScreen(container, id, onBack = { nav.popBackStack() }, onRead = { chapter ->
                            if (chapter != null) {
                                container.library.update(id) { e -> e?.copy(chapterIndex = chapter, blockIndex = 0, charOffset = 0) }
                            }
                            nav.navigate("reader/$id")
                        })
                    }
                    composable("reader/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments!!.getLong("id")
                        ReaderScreen(container, id, onBack = { nav.popBackStack() }, onOpenFonts = { nav.navigate("fonts") })
                    }
                    composable("fonts") {
                        FontsScreen(container, onBack = { nav.popBackStack() })
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = container.settings,
                            updateManager = container.updateManager,
                            onUpdateFound = { pendingUpdate = it },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }

                pendingUpdate?.let { info ->
                    UpdateDialog(info, container.updateManager, onDismiss = { pendingUpdate = null })
                }
            }
        }
    }
}
