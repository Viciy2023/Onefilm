package com.onefilm.android.tv.compose.feature.home

import com.onefilm.android.tv.compose.bridge.HomeFeed

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Content(val feed: HomeFeed) : HomeUiState
    data class Error(val message: String = "加载失败，请重试") : HomeUiState
    data object Empty : HomeUiState
}
