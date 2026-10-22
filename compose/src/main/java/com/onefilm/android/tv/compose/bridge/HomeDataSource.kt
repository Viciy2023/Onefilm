package com.onefilm.android.tv.compose.bridge

import android.content.Context
import java.io.Serializable

data class MediaCard(
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progress: Float? = null,
    val siteName: String? = null,
) : Serializable

data class HomeSection(val title: String, val items: List<MediaCard>) : Serializable

data class HomeCategory(val key: String, val id: String, val name: String) : Serializable

data class HomeFeed(
    val hero: List<MediaCard>,
    val sections: List<HomeSection>,
    val categories: List<HomeCategory> = emptyList(),
    val siteName: String = "",
    val notice: String = "",
) : Serializable

object HomeFeedCache {
    @Volatile
    var feed: HomeFeed? = null
}

interface HomeDataSource {
    suspend fun load(): HomeFeed
}

interface DetailLauncher {
    fun open(context: Context, card: MediaCard)
    fun openEpisode(context: Context, card: MediaCard, mark: String)
}
