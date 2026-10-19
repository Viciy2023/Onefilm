package com.onefilm.android.tv.compose.bridge

/** 将选集页地址解析为可直接播放的真实地址（复用旧 SiteApi.playerContent）。 */
interface PlaybackResolver {
    suspend fun resolve(key: String, flag: String, episodeUrl: String): String?
}
