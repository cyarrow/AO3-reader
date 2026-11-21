package io.github.cyarrow.ao3reader.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.ao3.SearchQuery
import io.github.cyarrow.ao3reader.ao3.WorkSummary
import io.github.cyarrow.ao3reader.data.ConvertMode
import io.github.cyarrow.ao3reader.reader.WorkConversion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: SearchQuery = SearchQuery(),
    val results: List<WorkSummary> = emptyList(),
    val page: Int = 0,
    val hasNext: Boolean = false,
    val total: String? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
    /** False while showing the start page (no search run yet). */
    val searched: Boolean = false,
)

/** Tag suggestions for what is being typed, grouped by AO3 tag type. */
data class Suggestions(
    val term: String = "",
    val fandoms: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val characters: List<String> = emptyList(),
    val freeforms: List<String> = emptyList(),
    val loading: Boolean = false,
    /** Set when AO3 could not be reached, so the UI does not claim "no matching tags". */
    val error: Throwable? = null,
) {
    val isEmpty get() = fandoms.isEmpty() && relationships.isEmpty() && characters.isEmpty() && freeforms.isEmpty()
}

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _suggestions = MutableStateFlow(Suggestions())
    val suggestions: StateFlow<Suggestions> = _suggestions.asStateFlow()

    val recent = container.readerPrefs.recentSearches

    private var job: Job? = null
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            // Remember the last language/sort/rating choices, but start on the start page.
            val last = container.readerPrefs.lastSearch.first()
            _state.update { it.copy(query = SearchQuery(language = last.language, sort = last.sort, ratings = last.ratings)) }
            container.pendingSearch?.let { container.pendingSearch = null; search(it) }
        }
    }

    fun search(query: SearchQuery) {
        job?.cancel()
        suggestJob?.cancel()
        _suggestions.value = Suggestions()
        _state.value = SearchUiState(query = query, loading = true, searched = true)
        viewModelScope.launch {
            container.readerPrefs.saveSearch(query)
            container.readerPrefs.addRecentSearch(query)
        }
        job = viewModelScope.launch { load(1) }
    }

    /** Back to the start page, keeping language and sort preferences. */
    fun clear() {
        job?.cancel()
        val q = _state.value.query
        _state.value = SearchUiState(query = SearchQuery(language = q.language, sort = q.sort, ratings = q.ratings))
    }

    fun clearRecent() = viewModelScope.launch { container.readerPrefs.clearRecentSearches() }

    /** Fetches fandom / relationship / character suggestions for [text], debounced. */
    private val suggestionCache = HashMap<String, Suggestions>()

    fun suggest(text: String) {
        suggestJob?.cancel()
        val term = text.trim()
        if (term.length < 2) { _suggestions.value = Suggestions(term); return }
        suggestionCache[term.lowercase()]?.let { _suggestions.value = it; return }
        _suggestions.value = Suggestions(term, loading = true)
        suggestJob = viewModelScope.launch {
            delay(600)
            // Only look up the other script when it actually differs (Chinese input).
            val variants = container.converter().variants(term).take(2)
            var failure: Throwable? = null
            suspend fun fetch(type: String): List<String> = variants.flatMap { v ->
                try {
                    container.ao3.autocomplete(type, v)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure = e; emptyList()
                }
            }.distinct().take(5)

            var current = Suggestions(term, loading = true)
            current = current.copy(fandoms = fetch("fandom")); _suggestions.value = current
            current = current.copy(relationships = fetch("relationship")); _suggestions.value = current
            current = current.copy(characters = fetch("character")); _suggestions.value = current

            // Autocomplete only matches word starts of canonical tags; AO3's tag search also finds
            // substrings, synonyms and Chinese tag names.
            if (current.isEmpty) {
                for (v in variants) {
                    try {
                        val hits = container.ao3.searchTags(v)
                        fun of(type: String) = hits.filter { it.type == type }.map { it.name }
                        current = current.copy(
                            fandoms = (current.fandoms + of("fandom")).distinct().take(5),
                            relationships = (current.relationships + of("relationship")).distinct().take(5),
                            characters = (current.characters + of("character")).distinct().take(5),
                            freeforms = (current.freeforms + of("freeform")).distinct().take(5),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failure = e
                    }
                }
            }
            current = current.copy(loading = false, error = if (current.isEmpty) failure else null)
            _suggestions.value = current
            if (current.error == null) suggestionCache[term.lowercase()] = current
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasNext) return
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch { load(s.page + 1) }
    }

    fun retry() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch { load(s.page + 1) }
    }

    suspend fun suggestFor(type: String, term: String): List<String> =
        runCatching { container.ao3.autocomplete(type, term) }.getOrDefault(emptyList())

    private suspend fun load(page: Int) {
        try {
            val q = _state.value.query
            val variants = if (q.query.isNotBlank()) container.converter().variants(q.query.trim()) else emptyList()
            val result = container.ao3.search(q, page, variants)
            result.works.forEach { container.summaries[it.id] = it }
            val shown = convertIfNeeded(result.works)
            _state.update {
                it.copy(
                    results = it.results + shown,
                    page = page,
                    hasNext = result.hasNext,
                    total = result.totalText ?: it.total,
                    loading = false,
                    error = null,
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, error = e) }
        }
    }

    private suspend fun convertIfNeeded(works: List<WorkSummary>): List<WorkSummary> {
        val settings = container.readerPrefs.settings.first()
        if (settings.convertMode == ConvertMode.Off) return works
        val converter = container.converter()
        val conversion = WorkConversion(converter, settings.taiwanPhrases)
        return withContext(Dispatchers.Default) {
            works.map { w ->
                val convert = settings.convertMode == ConvertMode.On || converter.looksSimplified(w.title + w.summary)
                if (convert) conversion.summary(w) else w
            }
        }
    }
}
