package com.onefilm.android.tv.compose.bridge

data class CategoryPage(
    val title: String,
    val items: List<MediaCard>,
)

interface CategoryDataSource {
    suspend fun load(key: String, tid: String, page: Int = 1): CategoryPage
}
