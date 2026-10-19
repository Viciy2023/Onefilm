package com.onefilm.android.tv.compose.feature.search

import androidx.lifecycle.ViewModel
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import kotlinx.coroutines.flow.StateFlow

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

    val state: StateFlow<SearchUiState> = SearchSession.state

    fun search(keyword: String) {
        SearchSession.start(dataSource, keyword)
    }

    fun retry() {
        SearchSession.retry(dataSource)
    }

    fun hasSession(keyword: String): Boolean = SearchSession.hasSession(keyword)
}
