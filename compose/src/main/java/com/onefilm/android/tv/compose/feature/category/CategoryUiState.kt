package com.onefilm.android.tv.compose.feature.category

import com.onefilm.android.tv.compose.bridge.CategoryPage

sealed interface CategoryUiState {
    data object Loading : CategoryUiState
    data class Content(val page: CategoryPage) : CategoryUiState
    data class Error(val message: String = "分类加载失败，请重试") : CategoryUiState
}
