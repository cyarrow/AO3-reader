package io.github.cyarrow.ao3reader.ui.update

import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.update.UpdateInfo
import io.github.cyarrow.ao3reader.update.UpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private sealed interface DialogState {
    data object Prompt : DialogState
    data class Downloading(val percent: Int) : DialogState
    data class Ready(val apk: File) : DialogState
    data class NeedPermission(val apk: File) : DialogState
    data class Failed(val message: String) : DialogState
}

@Composable
fun UpdateDialog(info: UpdateInfo, manager: UpdateManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<DialogState>(DialogState.Prompt) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun install(apk: File) {
        if (!manager.canInstallPackages()) {
            state = DialogState.NeedPermission(apk)
            return
        }
        try {
            context.startActivity(manager.installIntent(apk))
            state = DialogState.Ready(apk)
        } catch (e: ActivityNotFoundException) {
            state = DialogState.Failed(e.message ?: "No installer")
        }
    }

    fun startDownload() {
        state = DialogState.Downloading(0)
        job = scope.launch {
            state = try {
                val apk = manager.download(info) { state = DialogState.Downloading(it) }
                install(apk)
                return@launch
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                DialogState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (state !is DialogState.Downloading) onDismiss() },
        title = { Text(stringResource(R.string.update_available_title, info.version.toString())) },
        text = {
            Column {
                Text(stringResource(R.string.update_current_version, manager.currentVersion))
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is DialogState.Downloading -> {
                        Text(stringResource(R.string.update_downloading, s.percent))
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { s.percent / 100f })
                    }
                    is DialogState.NeedPermission -> Text(stringResource(R.string.update_need_permission))
                    is DialogState.Failed -> Text(
                        stringResource(R.string.update_download_failed, s.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> if (info.notes.isNotBlank()) {
                        Text(
                            info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (val s = state) {
                DialogState.Prompt -> TextButton(onClick = ::startDownload) {
                    Text(stringResource(R.string.update_download_install))
                }
                is DialogState.Downloading -> {}
                is DialogState.Ready -> TextButton(onClick = { install(s.apk) }) {
                    Text(stringResource(R.string.update_download_install))
                }
                is DialogState.NeedPermission -> TextButton(onClick = {
                    context.startActivity(manager.installPermissionIntent())
                    state = DialogState.Ready(s.apk)
                }) { Text(stringResource(R.string.update_open_settings)) }
                is DialogState.Failed -> TextButton(onClick = ::startDownload) {
                    Text(stringResource(R.string.retry))
                }
            }
        },
        dismissButton = {
            when (state) {
                DialogState.Prompt -> {
                    TextButton(onClick = { scope.launch { manager.skip(info) }; onDismiss() }) {
                        Text(stringResource(R.string.update_skip))
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
                }
                is DialogState.Downloading -> TextButton(onClick = {
                    job?.cancel()
                    state = DialogState.Prompt
                }) { Text(stringResource(R.string.cancel)) }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) }
            }
        },
    )
}
