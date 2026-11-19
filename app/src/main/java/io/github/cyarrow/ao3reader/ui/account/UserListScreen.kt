package io.github.cyarrow.ao3reader.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.cyarrow.ao3reader.AppContainer
import io.github.cyarrow.ao3reader.R
import io.github.cyarrow.ao3reader.account.AccountManager.ListKind
import io.github.cyarrow.ao3reader.ui.common.WorkCard
import io.github.cyarrow.ao3reader.ui.common.errorMessage
import io.github.cyarrow.ao3reader.ui.search.SearchUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class UserListViewModel(private val container: AppContainer, private val kind: ListKind) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState(loading = true, searched = true))
    val state = _state.asStateFlow()

    init { load(1) }

    fun loadMore() {
        val s = _state.value
        if (s.loading || !s.hasNext) return
        load(s.page + 1)
    }

    fun retry() = load(_state.value.page + 1)

    private fun load(page: Int) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val result = container.account.list(kind, page)
                result.works.forEach { container.summaries[it.id] = it }
                _state.update { it.copy(results = it.results + result.works, page = page, hasNext = result.hasNext, loading = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(container: AppContainer, kind: ListKind, onBack: () -> Unit, onOpenWork: (Long) -> Unit) {
    val vm: UserListViewModel = viewModel(key = "list-$kind") { UserListViewModel(container, kind) }
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearEnd, state.results.size) { if (nearEnd && state.error == null && state.results.isNotEmpty()) vm.loadMore() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.titleRes())) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding)) {
            items(state.results, key = { it.id }) { w ->
                WorkCard(w) { onOpenWork(w.id) }
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
                        state.results.isEmpty() -> Text(stringResource(R.string.list_empty), color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

fun ListKind.titleRes() = when (this) {
    ListKind.History -> R.string.account_history
    ListKind.MarkedForLater -> R.string.account_marked_for_later
    ListKind.Bookmarks -> R.string.account_bookmarks
}
