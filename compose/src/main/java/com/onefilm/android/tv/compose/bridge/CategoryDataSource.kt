package com.onefilm.android.tv.compose.bridge

data class CategoryFilterOption(val name: String, val value: String)

data class CategoryFilterGroup(
    val key: String,
    val name: String,
    val options: List<CategoryFilterOption>,
)

data class CategoryPage(
    val title: String,
    val items: List<MediaCard>,
    val filters: List<CategoryFilterGroup> = emptyList(),
)

interface CategoryDataSource {
    suspend fun load(
        key: String,
        tid: String,
        page: Int = 1,
        selected: Map<String, String> = emptyMap(),
    ): CategoryPage
}
