package com.onefilm.android.tv.compose.bridge

/** 同一集可选的播放地址（清晰度 / 备用地址）。name 为空表示默认。 */
data class PlayOption(val name: String, val url: String)

/** 解析后的播放地址：直链 + 站点请求头；needParse 表示还要再经过解析引擎。 */
data class ResolvedPlayback(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val needParse: Boolean = false,
    /** 站点返回的多地址列表；只有一个时也保留，便于播放器显示名称。 */
    val options: List<PlayOption> = emptyList(),
)

/** 把选集地址解析为真实地址（走旧 SiteApi.playerContent）。 */
interface PlaybackResolver {
    suspend fun resolve(key: String, flag: String, episodeUrl: String): ResolvedPlayback?
}
