package com.onefilm.android.tv.compose.bridge

/** 在线字幕候选（来源 assrt）。url 为空时表示还需要取详情。 */
data class SubtitleCandidate(
    val id: Int,
    val name: String,
    val url: String = "",
    val lang: String = "",
)

/**
 * 在线字幕仓库：搜索 → 取文件列表 → 下载到本地，返回可挂载的本地路径。
 * 未配置 token 时 available=false，播放器隐藏「搜索字幕」。
 */
interface SubtitleRepository {
    val available: Boolean

    suspend fun search(keyword: String): List<SubtitleCandidate>

    suspend fun files(id: Int): List<SubtitleCandidate>

    /** 下载/解压后返回本地字幕文件路径；失败返回 null。 */
    suspend fun download(item: SubtitleCandidate): String?

    fun cancel()
}
