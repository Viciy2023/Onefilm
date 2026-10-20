package com.onefilm.android.tv.compose.bridge

data class SearchRank(
    val title: String,
    val words: List<String>,
)

interface SearchMetaDataSource {
    fun history(): List<String>
    fun hot(): List<String>
    fun rankings(): List<SearchRank> = emptyList()
    fun record(keyword: String)
    fun clearHistory()
    suspend fun suggest(text: String): List<String>
    suspend fun refreshHot(): List<String>
    suspend fun refreshRankings(): List<SearchRank> = emptyList()
}
