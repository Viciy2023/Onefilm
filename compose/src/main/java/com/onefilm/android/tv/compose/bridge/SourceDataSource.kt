package com.onefilm.android.tv.compose.bridge

data class SourceItem(
    val siteName: String,
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
)

interface SourceDataSource {
    suspend fun load(title: String): List<SourceItem>
}
