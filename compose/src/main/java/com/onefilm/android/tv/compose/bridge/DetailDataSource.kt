package com.onefilm.android.tv.compose.bridge

import java.io.Serializable

data class DetailEpisode(val name: String, val url: String) : Serializable

data class DetailFlag(val name: String, val episodes: List<DetailEpisode>) : Serializable

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
    val sourceName: String? = null,
    val actor: String? = null,
    val director: String? = null,
    val playUrl: String? = null,
) : Serializable

interface DetailDataSource {
    suspend fun load(key: String, id: String): DetailInfo
}
