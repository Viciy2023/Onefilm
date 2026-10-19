package com.onefilm.android.tv.compose.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.onefilm.android.tv.compose.bridge.HomeDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val dataSource: HomeDataSource,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun retry() {
        viewModelScope.launch {
            _state.value = HomeUiState.Loading
            _state.value = runCatching { dataSource.load() }
                .fold(
                    onSuccess = { feed ->
                        if (feed.hero.isEmpty() && feed.sections.isEmpty()) HomeUiState.Empty
                        else HomeUiState.Content(feed)
                    },
                    onFailure = { HomeUiState.Error() },
                )
        }
    }
}
