package io.github.cyarrow.ao3reader.ui.shelf

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.data.LibraryEntry
import io.github.cyarrow.ao3reader.ui.common.errorMessage
import kotlinx.coroutines.launch

/** Every saved work (all are readable offline), with update checks and re-download. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(container: AppContainer, onRead: (Long) -> Unit, onOpenWork: (Long) -> Unit) {
    val library = container.library
    val entries by library.entries.collectAsState()
    var onlyUpdates by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkProgress by remember { mutableStateOf("") }
    val busy = remember { mutableStateMapOf<Long, Boolean>() }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var removing by remember { mutableStateOf<LibraryEntry?>(null) }
    val scope = rememberCoroutineScope()

    val all = entries.values.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }
    val shown = if (onlyUpdates) all.filter { it.hasUpdate } else all

    fun checkAll() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            val list = all.filter { it.chapterCount > 0 }
            for ((i, e) in list.withIndex()) {
                checkProgress = "${i + 1}/${list.size}"
                try {
                    val (chapters, _) = container.ao3.fetchWorkStats(e.workId)
                    library.update(e.workId) { it?.copy(remoteChapters = chapters, checkedAt = System.currentTimeMillis()) }
                } catch (ex: kotlinx.coroutines.CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    error = ex
                    if (ex is io.github.cyarrow.ao3reader.ao3.Ao3Exception.RateLimited) break
                }
            }
            checking = false
        }
    }

    fun redownload(e: LibraryEntry) {
        busy[e.workId] = true
        scope.launch {
            try {
                container.works.load(e.workId, refresh = true)
            } catch (ex: kotlinx.coroutines.CancellationException) {
                throw ex
            } catch (ex: Exception) {
                error = ex
            }
            busy.remove(e.workId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shelf_title)) },
                actions = {
                    if (checking) {
                        Text(checkProgress, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                        CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = ::checkAll, enabled = all.isNotEmpty()) {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.shelf_check))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!onlyUpdates, { onlyUpdates = false }, { Text(stringResource(R.string.shelf_all, all.size)) })
                    FilterChip(onlyUpdates, { onlyUpdates = true }, { Text(stringResource(R.string.shelf_updates, all.count { it.hasUpdate })) })
                }
                Text(stringResource(R.string.shelf_offline_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                error?.let {
                    Text(errorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(stringResource(if (onlyUpdates) R.string.shelf_no_updates else R.string.shelf_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp))
                }
            }
            items(shown, key = { it.workId }) { e ->
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(onClick = { onRead(e.workId) }, onLongClick = { removing = e })
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            e.authors.joinToString(", ") + " · " + stringResource(R.string.stat_chapters, e.savedChapters.ifEmpty { e.chapterCount.toString() }) +
                                (if (e.lastReadAt > 0) " · ${(e.fraction * 100).toInt()}%" else ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (e.hasUpdate) {
                            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.padding(top = 6.dp)) {
                                Text(stringResource(R.string.shelf_new_chapters, e.remoteChapters),
                                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                    }
                    when {
                        busy[e.workId] == true -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        e.hasUpdate -> TextButton(onClick = { redownload(e) }) { Text(stringResource(R.string.shelf_update)) }
                        else -> TextButton(onClick = { onOpenWork(e.workId) }) { Text(stringResource(R.string.home_details)) }
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }
        }
    }

    removing?.let { e ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(e.title) },
            text = { Text(stringResource(R.string.home_remove_confirm)) },
            confirmButton = { TextButton(onClick = { library.remove(e.workId); removing = null }) { Text(stringResource(R.string.remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
