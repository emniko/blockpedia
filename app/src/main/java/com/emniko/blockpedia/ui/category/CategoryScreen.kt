package com.emniko.blockpedia.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emniko.blockpedia.Graph
import com.emniko.blockpedia.data.WikiRepository
import com.emniko.blockpedia.ui.article.ErrorView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryUiState(
    val subcategories: List<String> = emptyList(),
    val pages: List<String> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
)

class CategoryViewModel(private val name: String, private val repo: WikiRepository) : ViewModel() {
    private val _state = MutableStateFlow(CategoryUiState())
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()
    private var cont: String? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = CategoryUiState(loading = true)
            cont = null
            _state.value = try {
                val subs = repo.subcategories(name)
                val first = repo.categoryPages(name, null)
                cont = first.next
                CategoryUiState(subs, first.titles, loading = false, endReached = first.next == null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CategoryUiState(loading = false, error = "Couldn't load this category. Check your connection.")
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        val token = cont
        if (s.loading || s.loadingMore || s.endReached || token == null) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch {
            try {
                val page = repo.categoryPages(name, token)
                cont = page.next
                val current = _state.value
                _state.value = current.copy(
                    pages = current.pages + page.titles,
                    loadingMore = false,
                    endReached = page.next == null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    name: String,
    onBack: () -> Unit,
    onOpenArticle: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    val vm: CategoryViewModel = viewModel(key = "category:$name") { CategoryViewModel(name, Graph.repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 8) vm.loadMore()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val error = state.error
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> ErrorView(error, showRetry = true, onRetry = vm::refresh)
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.subcategories.isNotEmpty()) {
                        item(key = "h-sub") { Header("Subcategories (${state.subcategories.size})") }
                        items(state.subcategories, key = { "c:$it" }) { sub ->
                            ListItem(
                                headlineContent = { Text(sub) },
                                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                                modifier = Modifier.clickable { onOpenCategory(sub) },
                            )
                        }
                    }
                    if (state.pages.isNotEmpty()) {
                        item(key = "h-pages") { Header("Pages") }
                        items(state.pages, key = { "p:$it" }) { page ->
                            ListItem(
                                headlineContent = { Text(page) },
                                leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                                modifier = Modifier.clickable { onOpenArticle(page) },
                            )
                        }
                    }
                    if (state.subcategories.isEmpty() && state.pages.isEmpty()) {
                        item(key = "empty") { Header("This category is empty.") }
                    }
                    if (state.loadingMore) {
                        item(key = "more") {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
