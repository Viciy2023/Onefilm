package com.onefilm.android.tv.compose.bridge

import android.content.Context

data class MediaCard(
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progress: Float? = null,
)

data class HomeSection(val title: String, val items: List<MediaCard>)

data class HomeFeed(val hero: List<MediaCard>, val sections: List<HomeSection>)

interface HomeDataSource {
    suspend fun load(): HomeFeed
}

interface DetailLauncher {
    fun open(context: Context, card: MediaCard)
    fun openEpisode(context: Context, card: MediaCard, mark: String)
}
