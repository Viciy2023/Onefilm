package com.onefilm.android.tv.compose.bridge

/** 旧版 History 的读取结果，用于详情页续播。 */
data class PlaybackHistoryInfo(
    val positionMs: Long,
    val durationMs: Long,
    val episodeUrl: String,
    val speed: Float,
)

/** 新播放器与旧版 History 表的桥接：按 siteKey+vodId 读写。 */
interface PlaybackHistoryStore {
    fun find(key: String, id: String): PlaybackHistoryInfo?

    fun save(
        key: String,
        id: String,
        title: String,
        poster: String?,
        flag: String,
        episodeName: String,
        episodeUrl: String,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
    )
}
