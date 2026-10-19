package com.onefilm.android.tv.compose.bridge

data class LibraryData(
    val favorites: List<MediaCard>,
    val history: List<MediaCard>,
)

interface LibraryDataSource {
    suspend fun load(): LibraryData
}
