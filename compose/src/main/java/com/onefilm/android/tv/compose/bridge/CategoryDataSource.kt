package com.onefilm.android.tv.compose.bridge

import java.io.Serializable

data class CategoryFilterOption(val name: String, val value: String) : Serializable

data class CategoryFilterGroup(
    val key: String,
    val name: String,
    val options: List<CategoryFilterOption>,
) : Serializable

data class CategoryPage(
    val title: String,
    val items: List<MediaCard>,
    val filters: List<CategoryFilterGroup> = emptyList(),
) : Serializable

interface CategoryDataSource {
    suspend fun load(
        key: String,
        tid: String,
        page: Int = 1,
        selected: Map<String, String> = emptyMap(),
    ): CategoryPage
}
