package io.github.cyarrow.ao3reader.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.ui.account.titleRes
import io.github.cyarrow.ao3reader.ao3.parseWorkId
import io.github.cyarrow.ao3reader.data.Library
import io.github.cyarrow.ao3reader.data.LibraryEntry

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    library: Library,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenWork: (Long) -> Unit,
    onRead: (Long) -> Unit,
    onSearchFandom: (String) -> Unit = {},
    user: String? = null,
    onOpenList: (io.github.cyarrow.ao3reader.account.AccountManager.ListKind) -> Unit = {},
) {
    val entries by library.entries.collectAsState()
    val recent = remember(entries) { entries.values.filter { it.lastReadAt > 0 }.sortedByDescending { it.lastReadAt } }
    var showLink by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<LibraryEntry?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { showLink = true }) { Icon(Icons.Filled.Link, stringResource(R.string.home_open_link)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Surface(
                    onClick = onOpenSearch,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(52.dp),
                ) {
                    Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.home_search), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (recent.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.home_empty), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                val first = recent.first()
                item {
                    SectionTitle(stringResource(R.string.home_continue))
                    ContinueCard(first, onRead = { onRead(first.workId) }, onDetails = { onOpenWork(first.workId) },
                        onLongClick = { removing = first })
                }
            }
            if (user != null) {
                item {
                    SectionTitle(stringResource(R.string.account_my_ao3, user))
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        io.github.cyarrow.ao3reader.account.AccountManager.ListKind.entries.forEach { k ->
                            androidx.compose.material3.FilledTonalButton(onClick = { onOpenList(k) }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(k.titleRes()), maxLines = 1)
                            }
                        }
                    }
                }
            }
            val fandoms = library.favoriteFandoms()
            if (fandoms.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.home_fandoms))
                    androidx.compose.foundation.lazy.LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(fandoms) { f ->
                            androidx.compose.material3.AssistChip(onClick = { onSearchFandom(f) }, label = { Text(f, maxLines = 1) })
                        }
                    }
                }
            }
            if (recent.size > 1) {
                    item { SectionTitle(stringResource(R.string.home_history)) }
                    items(recent.drop(1), key = { it.workId }) { e ->
                        HistoryRow(e, onClick = { onRead(e.workId) }, onLongClick = { removing = e })
                    }
                }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showLink) {
        var text by remember { mutableStateOf("") }
        val id = parseWorkId(text)
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text(stringResource(R.string.home_open_link)) },
            text = {
                OutlinedTextField(text, { text = it }, label = { Text(stringResource(R.string.home_link_hint)) }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = id != null, onClick = { showLink = false; id?.let(onOpenWork) }) { Text(stringResource(R.string.open)) }
            },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text(stringResource(R.string.cancel)) } },
        )
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContinueCard(e: LibraryEntry, onRead: () -> Unit, onDetails: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .combinedClickable(onClick = onRead, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(e.title, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(e.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_chapter_of, e.chapterIndex + 1, e.chapterCount),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text(progressLabel(e.fraction), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(progress = { e.fraction }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                drawStopIndicator = {})
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRead, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(stringResource(R.string.home_continue))
                }
                OutlinedButton(onClick = onDetails, modifier = Modifier.height(48.dp)) {
                    Text(stringResource(R.string.home_details))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(e: LibraryEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                e.authors.joinToString(", ") + " · " + stringResource(R.string.reader_chapter_of, e.chapterIndex + 1, e.chapterCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(progressLabel(e.fraction), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
    }
}

/** "42%" while reading, "已讀完" once the end was reached. */
@Composable
fun progressLabel(fraction: Float): String =
    if (fraction >= 0.999f) stringResource(R.string.progress_finished) else "${(fraction * 100).toInt()}%"
