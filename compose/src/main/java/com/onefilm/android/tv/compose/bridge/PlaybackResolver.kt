package com.onefilm.android.tv.compose.bridge

/** 解析后的播放地址：直链 + 站点请求头；needParse 表示还要再经过解析引擎。 */
data class ResolvedPlayback(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val needParse: Boolean = false,
)

/** 把选集地址解析为真实地址（走旧 SiteApi.playerContent）。 */
interface PlaybackResolver {
    suspend fun resolve(key: String, flag: String, episodeUrl: String): ResolvedPlayback?
}
