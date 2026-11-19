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
    val searched: Boolean = false,
)

class SearchViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            val pending = container.pendingSearch?.also { container.pendingSearch = null }
            val start = pending ?: container.readerPrefs.lastSearch.first()
            _state.update { it.copy(query = start) }
            search(start)
        }
    }

    fun search(query: SearchQuery) {
        job?.cancel()
        _state.value = SearchUiState(query = query, loading = true, searched = true)
        viewModelScope.launch { container.readerPrefs.saveSearch(query) }
        job = viewModelScope.launch { load(1) }
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

    suspend fun suggest(type: String, term: String): List<String> =
        runCatching { container.ao3.autocomplete(type, term) }.getOrDefault(emptyList())

    private suspend fun load(page: Int) {
        try {
            val result = container.ao3.search(_state.value.query, page)
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
