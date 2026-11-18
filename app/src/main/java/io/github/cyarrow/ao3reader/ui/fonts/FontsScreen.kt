package io.github.cyarrow.ao3reader.ui.fonts

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.fonts.FontSpec
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontsScreen(container: AppContainer, onBack: () -> Unit) {
    val fonts = container.fonts
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val installed by fonts.installed.collectAsState()
    val custom by fonts.custom.collectAsState()
    val settings by container.readerPrefs.settings.collectAsState(initial = null)
    val progress = remember { mutableStateMapOf<String, Float>() }
    var message by remember { mutableStateOf<String?>(null) }
    val failedText = stringResource(R.string.fonts_failed)

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
        scope.launch {
            runCatching { fonts.import(uri, name) }
                .onSuccess { spec -> container.readerPrefs.update { it.copy(fontId = spec.id) } }
                .onFailure { message = failedText.format(it.message.orEmpty()) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.fonts_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    TextButton(onClick = { importer.launch(arrayOf("font/ttf", "font/otf", "font/*", "application/x-font-ttf", "application/octet-stream")) }) {
                        Icon(Icons.Filled.Add, null)
                        Text(stringResource(R.string.fonts_import))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.fonts_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp)) }
            }
            items(fonts.builtIn + custom, key = { it.id }) { spec ->
                val isInstalled = spec.id in installed
                val selected = settings?.fontId == spec.id
                val p = progress[spec.id]
                Card(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = isInstalled) {
                        scope.launch { container.readerPrefs.update { it.copy(fontId = spec.id) } }
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(spec.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(spec.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            when {
                                selected -> Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                spec.kind == FontSpec.Kind.Download && !isInstalled && p == null -> TextButton(onClick = {
                                    progress[spec.id] = 0f
                                    scope.launch {
                                        runCatching { fonts.download(spec) { progress[spec.id] = it } }
                                            .onFailure { message = failedText.format(it.message.orEmpty()) }
                                        progress.remove(spec.id)
                                    }
                                }) {
                                    Icon(Icons.Filled.Download, null)
                                    Text(" ${spec.downloadSizeMb} MB")
                                }
                            }
                            if (!selected && isInstalled && (spec.kind == FontSpec.Kind.Download || spec.kind == FontSpec.Kind.Custom)) {
                                IconButton(onClick = { fonts.delete(spec) }) {
                                    Icon(Icons.Filled.Delete, stringResource(R.string.fonts_delete), tint = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        if (p != null) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        }
                        if (isInstalled) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.fonts_preview),
                                fontFamily = fonts.family(spec.id, settings?.fontWeight ?: 400),
                                fontSize = 17.sp,
                                lineHeight = 30.sp,
                                style = MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = 34.sp)),
                            )
                        }
                    }
                }
            }
        }
    }
}
