package com.onefilm.android.tv.compose.bridge

data class SearchPage(val items: List<MediaCard>)

interface SearchDataSource {
    suspend fun search(keyword: String, page: Int = 1): SearchPage
}
