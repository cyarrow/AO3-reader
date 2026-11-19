package io.github.cyarrow.ao3reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.data.SettingsRepository
import io.github.cyarrow.ao3reader.update.CheckResult
import io.github.cyarrow.ao3reader.update.UpdateInfo
import io.github.cyarrow.ao3reader.update.UpdateManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    updateManager: UpdateManager,
    onUpdateFound: (UpdateInfo) -> Unit,
    onBack: () -> Unit,
    account: io.github.cyarrow.ao3reader.account.AccountManager? = null,
    onLogin: () -> Unit = {},
    onFonts: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val autoCheck by settings.autoCheckUpdates.collectAsState(initial = true)
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    val upToDate = stringResource(R.string.update_up_to_date)
    val disabled = stringResource(R.string.update_debug_disabled)
    val failedFormat = stringResource(R.string.update_failed)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            if (account != null) {
                val user by account.user.collectAsState()
                SectionHeader(stringResource(R.string.account_section))
                if (user == null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.login_title)) },
                        supportingContent = { Text(stringResource(R.string.account_login_desc)) },
                        modifier = Modifier.clickable(onClick = onLogin),
                    )
                } else {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.account_logged_in_as, user!!)) },
                        trailingContent = {
                            androidx.compose.material3.TextButton(onClick = { scope.launch { account.logout() } }) {
                                Text(stringResource(R.string.account_logout))
                            }
                        },
                    )
                }
                HorizontalDivider()
            }
            SectionHeader(stringResource(R.string.reader_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.fonts_title)) },
                supportingContent = { Text(stringResource(R.string.settings_fonts_desc)) },
                modifier = Modifier.clickable(onClick = onFonts),
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_update))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_auto_check)) },
                supportingContent = { Text(stringResource(R.string.settings_auto_check_desc)) },
                trailingContent = {
                    Switch(checked = autoCheck, onCheckedChange = { scope.launch { settings.setAutoCheckUpdates(it) } })
                },
                modifier = Modifier.clickable { scope.launch { settings.setAutoCheckUpdates(!autoCheck) } },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_check_now)) },
                supportingContent = {
                    val text = if (checking) stringResource(R.string.update_checking) else status
                    if (text != null) Text(text)
                },
                modifier = Modifier.clickable(enabled = !checking) {
                    checking = true
                    status = null
                    scope.launch {
                        when (val r = updateManager.check()) {
                            is CheckResult.Available -> onUpdateFound(r.info)
                            CheckResult.UpToDate -> status = upToDate
                            CheckResult.Disabled -> status = disabled
                            is CheckResult.Failed -> status = failedFormat.format(r.message)
                        }
                        checking = false
                    }
                },
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.settings_section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_version)) },
                supportingContent = { Text(updateManager.currentVersion) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
