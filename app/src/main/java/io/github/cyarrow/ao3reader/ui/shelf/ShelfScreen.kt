package io.github.cyarrow.ao3reader.ui.shelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.data.LibraryEntry
import io.github.cyarrow.ao3reader.ui.common.errorMessage
import io.github.cyarrow.ao3reader.ui.home.progressLabel
import io.github.cyarrow.ao3reader.ui.motion.Motion
import kotlinx.coroutines.launch

enum class ShelfTab(val label: Int) {
    Reading(R.string.shelf_tab_reading),
    ToRead(R.string.shelf_tab_to_read),
    Finished(R.string.shelf_tab_finished),
    Updates(R.string.shelf_tab_updates);

    fun matches(e: LibraryEntry) = when (this) {
        Reading -> e.lastReadAt > 0 && !e.isFinished
        ToRead -> e.lastReadAt == 0L
        Finished -> e.isFinished
        Updates -> e.hasUpdate
    }
}

enum class ShelfSort(val label: Int) {
    LastRead(R.string.shelf_sort_last_read),
    Added(R.string.shelf_sort_added),
    Title(R.string.shelf_sort_title),
    Progress(R.string.shelf_sort_progress);

    fun sort(list: List<LibraryEntry>): List<LibraryEntry> = when (this) {
        LastRead -> list.sortedByDescending { maxOf(it.lastReadAt, it.addedAt) }
        Added -> list.sortedByDescending { it.addedAt }
        Title -> list.sortedBy { it.title.lowercase() }
        Progress -> list.sortedByDescending { if (it.isFinished) 1f else it.fraction }
    }
}

/** Every saved work (all readable offline): status tabs, search, sort, multi-select removal. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShelfScreen(container: AppContainer, onRead: (Long) -> Unit, onOpenWork: (Long) -> Unit) {
    val library = container.library
    val entries by library.entries.collectAsState()
    var tab by rememberSaveable { mutableStateOf(ShelfTab.Reading) }
    var sort by rememberSaveable { mutableStateOf(ShelfSort.LastRead) }
    var sortMenu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var queryVariants by remember { mutableStateOf(listOf<String>()) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmRemove by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var checkProgress by remember { mutableStateOf("") }
    val busy = remember { mutableStateMapOf<Long, Boolean>() }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val selecting = selected.isNotEmpty()

    // Match the query in either Chinese script.
    LaunchedEffect(query) {
        val q = query.trim()
        queryVariants = if (q.isEmpty()) emptyList() else container.converter().variants(q).map { it.lowercase() }
    }

    val all = entries.values.toList()
    fun matchesQuery(e: LibraryEntry): Boolean {
        if (queryVariants.isEmpty()) return true
        val hay = (listOf(e.title) + e.authors + e.fandoms + e.relationships).joinToString("\n").lowercase()
        return queryVariants.any { it in hay }
    }
    val counts = ShelfTab.entries.associateWith { t -> all.count { t.matches(it) && matchesQuery(it) } }
    val shown = sort.sort(all.filter { tab.matches(it) && matchesQuery(it) })

    BackHandler(enabled = selecting) { selected = emptySet() }

    fun checkAll() {
        if (checking) return
        checking = true
        error = null
        scope.launch {
            val list = all.filter { it.chapterCount > 0 && !it.savedChapters.endsWith("/" + it.savedChapters.substringBefore('/')) }
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
            if (selecting) {
                TopAppBar(
                    title = { Text(stringResource(R.string.shelf_selected, selected.size)) },
                    navigationIcon = {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                    },
                    actions = {
                        IconButton(onClick = { selected = if (selected.containsAll(shown.map { it.workId })) emptySet() else shown.map { it.workId }.toSet() }) {
                            Icon(Icons.Filled.SelectAll, stringResource(R.string.shelf_select_all))
                        }
                        IconButton(onClick = { confirmRemove = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.shelf_title)) },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.shelf_sort)) }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                ShelfSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(s.label), fontWeight = if (s == sort) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = { sort = s; sortMenu = false },
                                    )
                                }
                            }
                        }
                        if (checking) {
                            Text(checkProgress, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 8.dp))
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                        } else {
                            IconButton(onClick = ::checkAll, enabled = all.isNotEmpty()) {
                                Icon(Icons.Filled.Refresh, stringResource(R.string.shelf_check))
                            }
                        }
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.shelf_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, null) } },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp, containerColor = MaterialTheme.colorScheme.surface) {
                ShelfTab.entries.forEach { t ->
                    Tab(selected = tab == t, onClick = { tab = t; selected = emptySet() },
                        text = { Text(stringResource(t.label) + "  " + counts[t]) })
                }
            }
            error?.let {
                Text(errorMessage(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            stringResource(if (query.isNotBlank()) R.string.shelf_no_match else when (tab) {
                                ShelfTab.Reading -> R.string.shelf_empty_reading
                                ShelfTab.ToRead -> R.string.shelf_empty_to_read
                                ShelfTab.Finished -> R.string.shelf_empty_finished
                                ShelfTab.Updates -> R.string.shelf_no_updates
                            }),
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(32.dp),
                        )
                    }
                }
                items(shown, key = { it.workId }) { e ->
                    val isSelected = e.workId in selected
                    ShelfRow(
                        modifier = Modifier.animateItem(
                            fadeInSpec = Motion.enter(Motion.Standard),
                            placementSpec = Motion.enter(Motion.Standard),
                            fadeOutSpec = Motion.exit(Motion.Fast),
                        ),
                        e = e,
                        selecting = selecting,
                        isSelected = isSelected,
                        busy = busy[e.workId] == true,
                        onClick = {
                            when {
                                selecting -> selected = if (isSelected) selected - e.workId else selected + e.workId
                                else -> onRead(e.workId)
                            }
                        },
                        onLongClick = { selected = selected + e.workId },
                        onUpdate = { redownload(e) },
                        onDetails = { onOpenWork(e.workId) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.shelf_remove_title, selected.size)) },
            text = { Text(stringResource(R.string.shelf_remove_desc)) },
            confirmButton = {
                TextButton(onClick = { library.removeAll(selected); selected = emptySet(); confirmRemove = false }) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Compact two-line row: "Title · Fandom", then author · chapters · progress. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfRow(
    modifier: Modifier = Modifier,
    e: LibraryEntry,
    selecting: Boolean,
    isSelected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onUpdate: () -> Unit,
    onDetails: () -> Unit,
) {
    val bg by androidx.compose.animation.animateColorAsState(
        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else androidx.compose.ui.graphics.Color.Transparent,
        Motion.enter(Motion.Fast), label = "selection",
    )
    Row(
        modifier.fillMaxWidth().background(bg)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.Circle, null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 14.dp).size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            val fandom = e.fandoms.firstOrNull()
            Text(
                buildAnnotatedString {
                    append(e.title)
                    if (fandom != null) {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Normal,
                            fontSize = MaterialTheme.typography.bodySmall.fontSize)) {
                            append("  ·  "); append(fandom)
                        }
                    }
                },
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val chapters = stringResource(R.string.stat_chapters, e.savedChapters.ifEmpty { e.chapterCount.toString() })
            Text(
                e.authors.joinToString(", ") + " · " + chapters + (if (e.lastReadAt > 0) " · " + progressLabel(e) else ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (e.hasUpdate) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.shelf_new_chapters, e.remoteChapters), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
        }
        if (!selecting) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                e.hasUpdate -> TextButton(onClick = onUpdate) { Text(stringResource(R.string.shelf_update)) }
                else -> TextButton(onClick = onDetails) { Text(stringResource(R.string.home_details)) }
            }
        }
    }
}
