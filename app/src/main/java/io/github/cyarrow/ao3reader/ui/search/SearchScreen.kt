package io.github.cyarrow.ao3reader.ui.search

import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.focus.onFocusChanged
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(container: AppContainer, onBack: (() -> Unit)?, onOpenWork: (Long) -> Unit) {
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val state by vm.state.collectAsState()
    val suggestions by vm.suggestions.collectAsState()
    val recent by vm.recent.collectAsState(initial = emptyList())
    var text by remember { mutableStateOf("") }
    var typing by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()

    // A tag search requested while this tab was kept alive.
    LaunchedEffect(Unit) { container.pendingSearch?.let { container.pendingSearch = null; vm.search(it) } }
    // Keep the box in sync with the keyword of the active search.
    LaunchedEffect(state.query.query, state.searched) { if (!typing) text = if (state.searched) state.query.query else "" }

    fun run(q: SearchQuery) {
        focus.clearFocus()
        typing = false
        text = q.query
        vm.search(q)
    }

    BackHandler(enabled = typing || state.searched) {
        if (typing) { typing = false; focus.clearFocus(); text = if (state.searched) state.query.query else "" }
        else vm.clear()
    }

    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.results.size) {
        if (state.searched && nearEnd && state.error == null && state.results.isNotEmpty()) vm.loadMore()
    }

    Scaffold(
        topBar = {
            Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    else if (state.searched || typing) IconButton(onClick = {
                        typing = false; focus.clearFocus(); vm.clear()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it; typing = true; vm.suggest(it) },
                        placeholder = { Text(stringResource(R.string.search_placeholder), maxLines = 1) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = {
                            if (text.isNotEmpty()) IconButton(onClick = { text = ""; vm.suggest("") }) {
                                Icon(Icons.Filled.Clear, stringResource(R.string.search_clear))
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            run(state.query.copy(query = text.trim()))
                        }),
                        modifier = Modifier.weight(1f).onFocusChanged { if (it.isFocused) typing = true },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                typing && text.isNotBlank() -> SuggestionList(text.trim(), suggestions,
                    onKeyword = { run(state.query.copy(query = it)) },
                    onTag = { type, tag ->
                        val base = SearchQuery(language = state.query.language, sort = SearchQuery.Sort.Kudos, ratings = state.query.ratings)
                        run(when (type) {
                            "fandom" -> base.copy(fandoms = tag, sort = SearchQuery.Sort.Updated)
                            "relationship" -> base.copy(relationships = tag)
                            "freeform" -> base.copy(freeforms = tag)
                            else -> base.copy(characters = tag)
                        })
                    })
                !state.searched -> StartPage(
                    recent = recent,
                    fandoms = container.library.favoriteFandoms(),
                    language = state.query.language,
                    onSearch = ::run,
                    onClearRecent = { vm.clearRecent() },
                )
                else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    item {
                        ActiveFilters(state.query, state.total,
                            onChange = { vm.search(it) },
                            onMore = { showFilters = true })
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
                                state.results.isEmpty() -> Text(stringResource(R.string.search_no_results), textAlign = TextAlign.Center)
                                !state.hasNext -> Text(stringResource(R.string.search_end), color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(state.query.copy(query = text), suggest = vm::suggestFor, onDismiss = { showFilters = false }) { q ->
            showFilters = false
            run(q)
        }
    }
}

/** What to do before searching: recent searches, your fandoms, and ready-made browses. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartPage(
    recent: List<SearchQuery>,
    fandoms: List<String>,
    language: String,
    onSearch: (SearchQuery) -> Unit,
    onClearRecent: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text(stringResource(R.string.search_tips), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        if (recent.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StartTitle(stringResource(R.string.search_recent), Modifier.weight(1f))
                    TextButton(onClick = onClearRecent) { Text(stringResource(R.string.search_clear_recent)) }
                }
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recent.forEach { q ->
                        AssistChip(onClick = { onSearch(q) }, label = { Text(describe(q), maxLines = 1) },
                            leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) })
                    }
                }
            }
        }
        if (fandoms.isNotEmpty()) {
            item {
                StartTitle(stringResource(R.string.home_fandoms), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fandoms.forEach { f ->
                        AssistChip(onClick = { onSearch(SearchQuery(fandoms = f, language = language, sort = SearchQuery.Sort.Updated)) },
                            label = { Text(f, maxLines = 1) })
                    }
                }
            }
        }
        item {
            StartTitle(stringResource(R.string.search_browse), Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))
        }
        val browses = listOf(
            Triple(R.string.search_browse_latest, R.string.search_browse_latest_desc, SearchQuery(language = language, sort = SearchQuery.Sort.Updated)),
            Triple(R.string.search_browse_kudos, R.string.search_browse_kudos_desc, SearchQuery(language = language, sort = SearchQuery.Sort.Kudos)),
            Triple(R.string.search_browse_complete, R.string.search_browse_complete_desc,
                SearchQuery(language = language, sort = SearchQuery.Sort.Kudos, completion = SearchQuery.Completion.Complete)),
        )
        items(browses) { (title, desc, q) ->
            ListItem(
                headlineContent = { Text(stringResource(title)) },
                supportingContent = { Text(stringResource(desc)) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                modifier = Modifier.clickable { onSearch(q) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun StartTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

/** Live suggestions while typing: search the keyword, or jump straight to a fandom / CP / character. */
@Composable
internal fun SuggestionList(term: String, s: Suggestions, onKeyword: (String) -> Unit, onTag: (String, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.search_keyword, term)) },
                supportingContent = { Text(stringResource(R.string.search_keyword_desc)) },
                leadingContent = { Icon(Icons.Filled.Search, null) },
                modifier = Modifier.clickable { onKeyword(term) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        val groups = listOf(
            Triple("fandom", R.string.search_group_fandom, s.fandoms),
            Triple("relationship", R.string.search_group_relationship, s.relationships),
            Triple("character", R.string.search_group_character, s.characters),
            Triple("freeform", R.string.search_group_freeform, s.freeforms),
        )
        groups.forEach { (type, label, tags) ->
            if (tags.isNotEmpty()) {
                item {
                    Text(stringResource(label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                }
                items(tags) { tag ->
                    ListItem(
                        headlineContent = { Text(tag, maxLines = 2) },
                        modifier = Modifier.clickable { onTag(type, tag) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        if (s.loading) {
            item {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.search_finding_tags), color = MaterialTheme.colorScheme.outline)
                }
            }
        } else if (s.error != null) {
            item {
                Text(stringResource(R.string.search_tags_failed, errorMessage(s.error)), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp))
            }
        } else if (s.isEmpty && s.term.length >= 2) {
            item {
                Text(stringResource(R.string.search_no_tags), color = MaterialTheme.colorScheme.outline,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(20.dp))
            }
        }
    }
}

/** Short human label for a saved search. */
@Composable
private fun describe(q: SearchQuery): String = listOf(q.query, q.fandoms, q.relationships, q.characters, q.freeforms, q.title, q.creators)
    .filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { stringResource(sortLabel(q.sort)) }

/** The active search as removable chips, plus quick sort and a door to all filters. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ActiveFilters(q: SearchQuery, total: String?, onChange: (SearchQuery) -> Unit, onMore: () -> Unit) {
    var sortMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            @Composable fun removable(label: String, clear: SearchQuery.() -> SearchQuery) {
                InputChip(selected = true, onClick = { onChange(q.clear()) }, label = { Text(label, maxLines = 1) },
                    trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.search_remove_filter), Modifier.size(16.dp)) })
            }
            if (q.query.isNotBlank()) removable("「${q.query}」") { copy(query = "") }
            if (q.fandoms.isNotBlank()) removable(q.fandoms) { copy(fandoms = "") }
            if (q.relationships.isNotBlank()) removable(q.relationships) { copy(relationships = "") }
            if (q.characters.isNotBlank()) removable(q.characters) { copy(characters = "") }
            if (q.freeforms.isNotBlank()) removable(q.freeforms) { copy(freeforms = "") }
            if (q.title.isNotBlank()) removable(q.title) { copy(title = "") }
            if (q.creators.isNotBlank()) removable(q.creators) { copy(creators = "") }
            if (q.excludedTags.isNotBlank()) removable("−" + q.excludedTags) { copy(excludedTags = "") }
            if (q.completion != SearchQuery.Completion.Any) removable(stringResource(
                if (q.completion == SearchQuery.Completion.Complete) R.string.filter_complete else R.string.filter_in_progress)) {
                copy(completion = SearchQuery.Completion.Any)
            }
            if (q.ratings.isNotEmpty()) removable(q.ratings.joinToString("/") { ratingShort(it) }) { copy(ratings = emptySet()) }
            Box {
                FilterChip(selected = false, onClick = { sortMenu = true },
                    label = { Text(stringResource(sortLabel(q.sort))) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf(SearchQuery.Sort.BestMatch, SearchQuery.Sort.Updated, SearchQuery.Sort.Kudos, SearchQuery.Sort.Hits,
                        SearchQuery.Sort.Bookmarks, SearchQuery.Sort.Words, SearchQuery.Sort.Posted).forEach { s ->
                        DropdownMenuItem(text = { Text(stringResource(sortLabel(s))) }, onClick = {
                            sortMenu = false
                            onChange(q.copy(sort = s))
                        })
                    }
                }
            }
            FilterChip(selected = false, onClick = onMore, label = { Text(languageLabel(q.language)) })
            FilterChip(selected = false, onClick = onMore, label = { Text(stringResource(R.string.search_more_filters)) },
                leadingIcon = { Icon(Icons.Filled.Tune, null, Modifier.size(18.dp)) })
        }
        if (total != null) Text(total, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp))
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
private fun FilterSheet(
    initial: SearchQuery,
    suggest: suspend (String, String) -> List<String>,
    onDismiss: () -> Unit,
    onApply: (SearchQuery) -> Unit,
) {
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
            TagField(stringResource(R.string.filter_fandoms), q.fandoms, "fandom", suggest) { q = q.copy(fandoms = it) }
            TagField(stringResource(R.string.filter_relationships), q.relationships, "relationship", suggest) { q = q.copy(relationships = it) }
            TagField(stringResource(R.string.filter_characters), q.characters, "character", suggest) { q = q.copy(characters = it) }
            TagField(stringResource(R.string.filter_tags), q.freeforms, "freeform", suggest) { q = q.copy(freeforms = it) }
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

/** Comma-separated tag field with AO3 autocomplete for the tag being typed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagField(
    label: String,
    value: String,
    type: String,
    suggest: suspend (String, String) -> List<String>,
    onChange: (String) -> Unit,
) {
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    val current = value.substringAfterLast(',').trim()
    LaunchedEffect(current) {
        suggestions = emptyList()
        if (current.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        suggestions = suggest(type, current).filter { it != current }.take(6)
    }
    Field(label, value, stringResource(R.string.filter_multiple_hint), onChange)
    if (suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
            suggestions.forEach { tag ->
                androidx.compose.material3.SuggestionChip(
                    onClick = {
                        val prefix = value.substringBeforeLast(',', "").trim()
                        onChange(if (prefix.isEmpty()) tag else "$prefix, $tag")
                        suggestions = emptyList()
                    },
                    label = { Text(tag, maxLines = 1) },
                )
            }
        }
    }
}
