package com.onefilm.android.tv.compose.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.DetailInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val key: String,
    private val id: String,
    private val dataSource: DetailDataSource,
    initial: DetailInfo? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<DetailUiState>(
        initial?.let { DetailUiState.Content(it) } ?: DetailUiState.Loading,
    )
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    fun retry() {
        viewModelScope.launch {
            _state.value = DetailUiState.Loading
            _state.value = runCatching { dataSource.load(key, id) }
                .fold(
                    onSuccess = { DetailUiState.Content(it) },
                    onFailure = { DetailUiState.Error() },
                )
        }
    }
}
