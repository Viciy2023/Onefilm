package com.onefilm.android.tv.compose.feature.detail

import com.onefilm.android.tv.compose.bridge.DetailInfo

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Content(val info: DetailInfo) : DetailUiState
    data class Error(val message: String = "详情加载失败，请重试") : DetailUiState
}
