package com.onefilm.android.tv.compose.feature.category

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun retry() = load()

    fun toggleFilter(filterKey: String, value: String, selectedNow: Boolean) {
        if (selectedNow) selected[filterKey] = value else selected.remove(filterKey)
        _selectedFilters.value = selected.toMap()
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.value = CategoryUiState.Loading
            _state.value = runCatching { dataSource.load(key, tid, 1, selected) }
                .fold(
                    onSuccess = { CategoryUiState.Content(it) },
                    onFailure = { CategoryUiState.Error() },
                )
        }
    }
}
