package com.onefilm.android.tv.compose.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Content(
        val keyword: String,
        val items: List<MediaCard>,
        val done: Boolean = false,
        val completed: Int = 0,
        val total: Int = 0,
    ) : SearchUiState
    data class Empty(val keyword: String) : SearchUiState
    data class Error(val message: String = "搜索失败，请重试") : SearchUiState
}

class SearchViewModel(
    private val dataSource: SearchDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var lastKeyword = ""
    private var job: Job? = null

    fun search(keyword: String) {
        lastKeyword = keyword.trim()
        job?.cancel()
        if (lastKeyword.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        job = viewModelScope.launch {
            _state.value = SearchUiState.Loading
            dataSource.search(lastKeyword).collect { progress ->
                _state.value = when {
                    progress.items.isNotEmpty() -> SearchUiState.Content(lastKeyword, progress.items, progress.done, progress.completed, progress.total)
                    progress.done -> SearchUiState.Empty(lastKeyword)
                    else -> SearchUiState.Loading
                }
            }
        }
    }

    fun retry() = search(lastKeyword)
}
