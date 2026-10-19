package com.onefilm.android.tv.compose.bridge

data class DetailEpisode(val name: String, val url: String)

data class DetailFlag(val name: String, val episodes: List<DetailEpisode>)

data class DetailInfo(
    val key: String,
    val id: String,
    val title: String,
    val posterUrl: String?,
    val description: String?,
    val year: String?,
    val area: String?,
    val remarks: String?,
    val flags: List<DetailFlag>,
)

interface DetailDataSource {
    suspend fun load(key: String, id: String): DetailInfo
}
