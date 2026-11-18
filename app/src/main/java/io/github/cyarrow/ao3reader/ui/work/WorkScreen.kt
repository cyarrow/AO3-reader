package io.github.cyarrow.ao3reader.ui.work

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.ao3.Ao3Client
import io.github.cyarrow.ao3reader.ao3.Work
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import io.github.cyarrow.ao3reader.data.ConvertMode
import io.github.cyarrow.ao3reader.reader.WorkConversion
import io.github.cyarrow.ao3reader.reader.chapterListTitle
import io.github.cyarrow.ao3reader.ui.reader.chapterFormat
import io.github.cyarrow.ao3reader.reader.textSample
import io.github.cyarrow.ao3reader.ui.common.RatingBadge
import io.github.cyarrow.ao3reader.ui.common.TagChip
import io.github.cyarrow.ao3reader.ui.common.errorMessage
import io.github.cyarrow.ao3reader.ui.common.formatCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class WorkUiState(
    val summary: WorkSummary? = null,
    val work: Work? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

class WorkViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    private val _state = MutableStateFlow(WorkUiState(summary = container.summaries[id]))
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val work = container.library.cachedWork(id)
                    ?: container.ao3.fetchWork(id).also { container.library.saveWork(it) }
                _state.update { it.copy(work = convert(work), loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun convert(work: Work): Work {
        val settings = container.readerPrefs.settings.first()
        val mode = container.library.entry(id)?.convert ?: settings.convertMode
        val converter = container.converter()
        return withContext(Dispatchers.Default) {
            val should = mode == ConvertMode.On || (mode == ConvertMode.Auto && converter.looksSimplified(work.textSample()))
            if (should) WorkConversion(converter, settings.taiwanPhrases).work(work) else work
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkScreen(container: AppContainer, id: Long, onBack: () -> Unit, onRead: (chapter: Int?) -> Unit) {
    val vm: WorkViewModel = viewModel(key = "work-$id") { WorkViewModel(container, id) }
    val state by vm.state.collectAsState()
    val entries by container.library.entries.collectAsState()
    val entry = entries[id]
    val context = LocalContext.current

    val work = state.work
    val summary = state.summary
    val chapterNumber = chapterFormat()
    val title = work?.title ?: summary?.title.orEmpty()
    val authors = work?.authors ?: summary?.authors.orEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${Ao3Client.BASE}/works/$id")))
                    }) { Icon(Icons.Filled.OpenInBrowser, stringResource(R.string.work_open_browser)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (work?.rating ?: summary?.rating)?.let { RatingBadge(it); Spacer(Modifier.width(8.dp)) }
                        Text(
                            (work?.fandoms ?: summary?.fandoms).orEmpty().joinToString(", "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(authors.joinToString(", "), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

                    val words = work?.words ?: summary?.words ?: 0
                    val chapters = work?.chaptersText ?: summary?.chapters.orEmpty()
                    Text(
                        listOfNotNull(
                            stringResource(R.string.stat_words, formatCount(words)),
                            stringResource(R.string.stat_chapters, chapters),
                            summary?.kudos?.let { "♥ ${formatCount(it)}" },
                            (work?.updated ?: summary?.updated)?.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    // Primary action
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onRead(null) }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (entry != null && entry.lastReadAt > 0)
                                    stringResource(R.string.work_continue, entry.chapterIndex + 1, (entry.fraction * 100).toInt())
                                else stringResource(R.string.work_start)
                            )
                        }
                        if (entry != null && entry.lastReadAt > 0) {
                            OutlinedButton(onClick = { onRead(0) }, modifier = Modifier.height(48.dp)) {
                                Text(stringResource(R.string.work_from_start))
                            }
                        }
                    }

                    val tags = (work?.relationships ?: summary?.relationships).orEmpty()
                    val chars = (work?.characters ?: summary?.characters).orEmpty()
                    val free = (work?.freeforms ?: summary?.freeforms).orEmpty()
                    val warnings = (work?.warnings ?: summary?.warnings).orEmpty()
                    if (tags.isNotEmpty() || chars.isNotEmpty() || free.isNotEmpty() || warnings.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            warnings.forEach { TagChip(it) }
                            tags.forEach { TagChip(it, highlighted = true) }
                            chars.forEach { TagChip(it) }
                            free.forEach { TagChip(it) }
                        }
                    }

                    val summaryText = work?.summary?.joinToString("\n") { it.text } ?: summary?.summary.orEmpty()
                    if (summaryText.isNotBlank()) {
                        Text(stringResource(R.string.work_summary), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
                        Text(summaryText, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (!work?.notes.isNullOrEmpty()) {
                        Text(stringResource(R.string.reader_work_notes), style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                        Text(work!!.notes.joinToString("\n") { it.text }, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(stringResource(R.string.reader_toc), style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
                }
            }
            when {
                work != null -> itemsIndexed(work.chapters) { i, ch ->
                    val current = entry?.chapterIndex == i && entry.lastReadAt > 0
                    Row(
                        Modifier.fillMaxWidth().clickable { onRead(i) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            chapterListTitle(ch.title, chapterNumber, work.title),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                state.loading -> item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                }
                state.error != null -> item {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(errorMessage(state.error!!), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = vm::load) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
