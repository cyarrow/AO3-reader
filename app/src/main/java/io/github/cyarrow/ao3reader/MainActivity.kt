package io.github.cyarrow.ao3reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.cyarrow.ao3reader.ui.home.HomeScreen
import io.github.cyarrow.ao3reader.ui.settings.SettingsScreen
import io.github.cyarrow.ao3reader.ui.theme.AO3ReaderTheme
import io.github.cyarrow.ao3reader.ui.update.UpdateDialog
import io.github.cyarrow.ao3reader.update.UpdateInfo
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AO3ReaderApp).container

        setContent {
            AO3ReaderTheme {
                val navController = rememberNavController()
                var pendingUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
                var autoChecked by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    if (!autoChecked && container.settings.autoCheckUpdates.first()) {
                        autoChecked = true
                        container.updateManager.autoCheck()?.let { pendingUpdate = it }
                    }
                }

                NavHost(navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(onOpenSettings = { navController.navigate("settings") })
                    }
                    composable("settings") {
                        SettingsScreen(
                            settings = container.settings,
                            updateManager = container.updateManager,
                            onUpdateFound = { pendingUpdate = it },
                            onBack = { navController.popBackStack() },
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
