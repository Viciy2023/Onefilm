package com.onefilm.android.tv.compose.bridge

import kotlinx.coroutines.flow.Flow

data class SearchProgress(
    val items: List<MediaCard>,
    val done: Boolean,
    val completed: Int = 0,
    val total: Int = 0,
)

interface SearchDataSource {
    fun search(keyword: String, page: Int = 1): Flow<SearchProgress>
}
