package io.github.cyarrow.ao3reader.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.ao3.SearchQuery
import io.github.cyarrow.ao3reader.ui.common.WorkCard
import io.github.cyarrow.ao3reader.ui.common.errorMessage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, onBack: () -> Unit, onOpenWork: (Long) -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsState()
    var text by remember(state.query.query) { mutableStateOf(state.query.query) }
    var showFilters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    // Load the next page when the end of the list comes into view.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.results.size) {
        if (nearEnd && state.error == null && state.results.isNotEmpty()) vm.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                title = {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (text.isNotEmpty()) IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Clear, null) }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focus.clearFocus()
                            vm.search(state.query.copy(query = text))
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.Tune, stringResource(R.string.search_filters)) }
                },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ActiveFilters(state.query, state.total) { showFilters = true }
            }
            items(state.results, key = { it.id }) { work ->
                WorkCard(work) { onOpenWork(work.id) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.error != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMessage(state.error!!), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = vm::retry) { Text(stringResource(R.string.retry)) }
                        }
                        state.searched && state.results.isEmpty() -> Text(stringResource(R.string.search_no_results))
                        !state.hasNext && state.results.isNotEmpty() -> Text(
                            stringResource(R.string.search_end), color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(state.query.copy(query = text), onDismiss = { showFilters = false }) { q ->
            showFilters = false
            vm.search(q)
        }
    }
}

@Composable
private fun ActiveFilters(q: SearchQuery, total: String?, onClick: () -> Unit) {
    val parts = buildList {
        add(languageLabel(q.language))
        add(stringResource(sortLabel(q.sort)))
        if (q.ratings.isNotEmpty()) add(q.ratings.joinToString("/") { ratingShort(it) })
        if (q.completion == SearchQuery.Completion.Complete) add(stringResource(R.string.filter_complete))
        if (q.completion == SearchQuery.Completion.InProgress) add(stringResource(R.string.filter_in_progress))
        if (q.fandoms.isNotBlank()) add(q.fandoms)
        if (q.excludedTags.isNotBlank()) add("−" + q.excludedTags)
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 2.dp),
        )
        if (total != null) Text(total, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun languageLabel(code: String) = when (code) {
    "zh" -> stringResource(R.string.filter_lang_zh)
    "en" -> stringResource(R.string.filter_lang_en)
    else -> stringResource(R.string.filter_lang_all)
}

private fun ratingShort(r: SearchQuery.Rating) = when (r) {
    SearchQuery.Rating.General -> "G"
    SearchQuery.Rating.Teen -> "T"
    SearchQuery.Rating.Mature -> "M"
    SearchQuery.Rating.Explicit -> "E"
    SearchQuery.Rating.NotRated -> "NR"
}

private fun sortLabel(s: SearchQuery.Sort) = when (s) {
    SearchQuery.Sort.BestMatch -> R.string.sort_best
    SearchQuery.Sort.Updated -> R.string.sort_updated
    SearchQuery.Sort.Posted -> R.string.sort_posted
    SearchQuery.Sort.Kudos -> R.string.sort_kudos
    SearchQuery.Sort.Hits -> R.string.sort_hits
    SearchQuery.Sort.Bookmarks -> R.string.sort_bookmarks
    SearchQuery.Sort.Comments -> R.string.sort_comments
    SearchQuery.Sort.Words -> R.string.sort_words
    SearchQuery.Sort.Title -> R.string.sort_title
    SearchQuery.Sort.Author -> R.string.sort_author
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(initial: SearchQuery, onDismiss: () -> Unit, onApply: (SearchQuery) -> Unit) {
    var q by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding()
        ) {
            Section(stringResource(R.string.filter_language))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("zh", "en", "").forEach { code ->
                    FilterChip(q.language == code, { q = q.copy(language = code) }, { Text(languageLabel(code)) })
                }
            }
            Section(stringResource(R.string.filter_sort))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchQuery.Sort.entries.forEach { s ->
                    FilterChip(q.sort == s, { q = q.copy(sort = s) }, { Text(stringResource(sortLabel(s))) })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!q.ascending, { q = q.copy(ascending = false) }, { Text(stringResource(R.string.filter_desc)) })
                FilterChip(q.ascending, { q = q.copy(ascending = true) }, { Text(stringResource(R.string.filter_asc)) })
            }
            Section(stringResource(R.string.filter_rating))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(SearchQuery.Rating.General, SearchQuery.Rating.Teen, SearchQuery.Rating.Mature, SearchQuery.Rating.Explicit, SearchQuery.Rating.NotRated)
                    .forEach { r ->
                        val on = r in q.ratings
                        FilterChip(on, { q = q.copy(ratings = if (on) q.ratings - r else q.ratings + r) }, { Text(ratingShort(r)) })
                    }
            }
            Section(stringResource(R.string.filter_status))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SearchQuery.Completion.Any to R.string.filter_any,
                    SearchQuery.Completion.Complete to R.string.filter_complete,
                    SearchQuery.Completion.InProgress to R.string.filter_in_progress,
                ).forEach { (c, label) ->
                    FilterChip(q.completion == c, { q = q.copy(completion = c) }, { Text(stringResource(label)) })
                }
            }
            Section(stringResource(R.string.filter_crossover))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    SearchQuery.Crossover.Include to R.string.filter_crossover_include,
                    SearchQuery.Crossover.Exclude to R.string.filter_crossover_exclude,
                    SearchQuery.Crossover.Only to R.string.filter_crossover_only,
                ).forEach { (c, label) ->
                    FilterChip(q.crossover == c, { q = q.copy(crossover = c) }, { Text(stringResource(label)) })
                }
            }
            Section(stringResource(R.string.filter_details))
            Field(stringResource(R.string.filter_title), q.title) { q = q.copy(title = it) }
            Field(stringResource(R.string.filter_creators), q.creators) { q = q.copy(creators = it) }
            Field(stringResource(R.string.filter_fandoms), q.fandoms) { q = q.copy(fandoms = it) }
            Field(stringResource(R.string.filter_relationships), q.relationships) { q = q.copy(relationships = it) }
            Field(stringResource(R.string.filter_characters), q.characters) { q = q.copy(characters = it) }
            Field(stringResource(R.string.filter_tags), q.freeforms) { q = q.copy(freeforms = it) }
            Field(stringResource(R.string.filter_excluded), q.excludedTags, stringResource(R.string.filter_excluded_hint)) { q = q.copy(excludedTags = it) }
            Field(stringResource(R.string.filter_words), q.words, stringResource(R.string.filter_words_hint)) { q = q.copy(words = it) }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { q = SearchQuery(query = q.query) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.filter_reset))
                }
                Button(onClick = { onApply(q) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.filter_apply)) }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Field(label: String, value: String, hint: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        supportingText = hint?.let { { Text(it) } },
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}
