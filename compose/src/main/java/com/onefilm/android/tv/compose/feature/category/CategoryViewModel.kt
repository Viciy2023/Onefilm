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

    fun retry() {
        viewModelScope.launch {
            _state.value = CategoryUiState.Loading
            _state.value = runCatching { dataSource.load(key, tid) }
                .fold(
                    onSuccess = { CategoryUiState.Content(it) },
                    onFailure = { CategoryUiState.Error() },
                )
        }
    }
}
