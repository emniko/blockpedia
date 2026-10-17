package com.emniko.blockpedia.ui.article

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emniko.blockpedia.data.ArticleResult
import com.emniko.blockpedia.data.UserStore
import com.emniko.blockpedia.data.WikiException
import com.emniko.blockpedia.data.WikiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ArticleUiState {
    data object Loading : ArticleUiState
    data class Error(val message: String, val notFound: Boolean) : ArticleUiState
    data class Success(val result: ArticleResult, val refreshing: Boolean = false) : ArticleUiState
}

class ArticleViewModel(
    private val title: String,
    private val repo: WikiRepository,
    private val userStore: UserStore,
) : ViewModel() {

    private val _state = MutableStateFlow<ArticleUiState>(ArticleUiState.Loading)
    val state: StateFlow<ArticleUiState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        load(force = false)
    }

    fun load(force: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            val previous = _state.value
            _state.value = if (previous is ArticleUiState.Success) previous.copy(refreshing = true) else ArticleUiState.Loading
            _state.value = try {
                val result = repo.article(title, forceRefresh = force)
                userStore.addHistory(result.article.title)
                ArticleUiState.Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: WikiException) {
                val notFound = e.code == "missingtitle"
                ArticleUiState.Error(
                    if (notFound) "This page doesn't exist on the Minecraft Wiki." else (e.message ?: "The wiki returned an error."),
                    notFound,
                )
            } catch (e: Exception) {
                if (previous is ArticleUiState.Success) {
                    previous.copy(refreshing = false)
                } else {
                    ArticleUiState.Error("Couldn't load this page. Check your connection and try again.", notFound = false)
                }
            }
        }
    }
}
