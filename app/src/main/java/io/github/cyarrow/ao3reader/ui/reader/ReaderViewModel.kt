package io.github.cyarrow.ao3reader.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.ao3.Work
import io.github.cyarrow.ao3reader.data.ConvertMode
import io.github.cyarrow.ao3reader.data.LibraryEntry
import io.github.cyarrow.ao3reader.reader.WorkConversion
import io.github.cyarrow.ao3reader.reader.textSample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Failed(val error: Throwable) : ReaderState
    data class Ready(
        val work: Work,
        val simplifiedSource: Boolean,
        val converted: Boolean,
        val workConvert: ConvertMode?,
        /** Bumped whenever the content changes, so pagination and positions are rebuilt. */
        val version: Int,
    ) : ReaderState
}

class ReaderViewModel(private val container: AppContainer, private val workId: Long) : ViewModel() {
    private val _state = MutableStateFlow<ReaderState>(ReaderState.Loading)
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var raw: Work? = null
    private var version = 0

    /** Position at open time, so the reader can restore it once. */
    val initialEntry: LibraryEntry? = container.library.entry(workId)

    init { load() }

    fun load(forceRefresh: Boolean = false) {
        _state.value = ReaderState.Loading
        viewModelScope.launch {
            try {
                val work = (if (forceRefresh) null else container.library.cachedWork(workId))
                    ?: container.ao3.fetchWork(workId).also { container.library.saveWork(it) }
                raw = work
                if (container.library.entry(workId) == null) container.library.saveWork(work)
                applyConversion()
            } catch (e: Exception) {
                _state.value = ReaderState.Failed(e)
            }
        }
    }

    /** Re-applies conversion after the per-work or global preference changes. */
    fun applyConversion() {
        val work = raw ?: return
        viewModelScope.launch {
            val settings = container.readerPrefs.settings.first()
            val workMode = container.library.entry(workId)?.convert
            val converter = container.converter()
            val simplified = withContext(Dispatchers.Default) { converter.looksSimplified(work.textSample()) }
            val shouldConvert = when (workMode ?: settings.convertMode) {
                ConvertMode.On -> true
                ConvertMode.Off -> false
                ConvertMode.Auto -> simplified
            }
            val shown = if (shouldConvert) {
                withContext(Dispatchers.Default) { WorkConversion(converter, settings.taiwanPhrases).work(work) }
            } else work
            _state.value = ReaderState.Ready(shown, simplified, shouldConvert, workMode, ++version)
        }
    }

    fun setWorkConvert(mode: ConvertMode?) {
        container.library.update(workId) { it?.copy(convert = mode) }
        applyConversion()
    }

    fun saveProgress(chapter: Int, block: Int, charOffset: Int, fraction: Float) {
        container.library.saveProgress(workId, chapter, block, charOffset, fraction)
    }
}
