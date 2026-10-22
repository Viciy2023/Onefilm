package com.onefilm.android.tv.compose.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val key: String,
    private val tid: String,
    private val dataSource: CategoryDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow<CategoryUiState>(CategoryUiState.Loading)
    val state: StateFlow<CategoryUiState> = _state.asStateFlow()

    private val selected = linkedMapOf<String, String>()
    private val _selectedFilters = MutableStateFlow<Map<String, String>>(emptyMap())
    val selectedFilters: StateFlow<Map<String, String>> = _selectedFilters.asStateFlow()

    private var page = 1
    private var loading = false
    private var loadJob: Job? = null

    fun retry() = load(reset = true)

    fun toggleFilter(filterKey: String, value: String, selectedNow: Boolean) {
        if (selectedNow) selected[filterKey] = value else selected.remove(filterKey)
        _selectedFilters.value = selected.toMap()
        load(reset = true, keepContent = true)
    }

    fun loadMore() {
        val current = _state.value
        if (loading || current !is CategoryUiState.Content || current.endReached) return
        page += 1
        load(reset = false)
    }

    private fun load(reset: Boolean, keepContent: Boolean = false) {
        if (loading) return
        loadJob?.cancel()
        loading = true
        if (reset) page = 1
        loadJob = viewModelScope.launch {
            if (reset && !keepContent) _state.value = CategoryUiState.Loading
            runCatching { dataSource.load(key, tid, page, selected) }
                .onSuccess { pageData ->
                    val previous = (_state.value as? CategoryUiState.Content)?.page
                    val merged = if (reset || previous == null) {
                        pageData
                    } else {
                        pageData.copy(items = previous.items + pageData.items)
                    }
                    _state.value = CategoryUiState.Content(
                        page = merged,
                        loadingMore = false,
                        endReached = pageData.items.isEmpty(),
                    )
                }
                .onFailure {
                    _state.value = CategoryUiState.Error()
                }
            loading = false
        }
    }
}
