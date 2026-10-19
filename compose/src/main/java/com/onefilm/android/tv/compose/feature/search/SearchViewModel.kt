package com.onefilm.android.tv.compose.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Content(val keyword: String, val items: List<MediaCard>) : SearchUiState
    data class Empty(val keyword: String) : SearchUiState
    data class Error(val message: String = "搜索失败，请重试") : SearchUiState
}

class SearchViewModel(
    private val dataSource: SearchDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var lastKeyword = ""

    fun search(keyword: String) {
        lastKeyword = keyword.trim()
        if (lastKeyword.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }
        viewModelScope.launch {
            _state.value = SearchUiState.Loading
            _state.value = runCatching { dataSource.search(lastKeyword) }
                .fold(
                    onSuccess = { page ->
                        if (page.items.isEmpty()) SearchUiState.Empty(lastKeyword)
                        else SearchUiState.Content(lastKeyword, page.items)
                    },
                    onFailure = { SearchUiState.Error() },
                )
        }
    }

    fun retry() = search(lastKeyword)
}
