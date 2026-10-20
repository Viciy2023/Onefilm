package com.onefilm.android.tv.compose.bridge

data class DanmakuSource(val name: String, val url: String)

data class DanmakuDisplayConfig(
    val show: Boolean,
    val scale: Float,
    val transparency: Float,
    val durationMs: Long,
    val lines: Int,
)

/** 旧版弹幕接口桥接：搜索弹幕源并下载弹幕文件内容。 */
interface DanmakuDataSource {
    suspend fun search(name: String, episode: String): List<DanmakuSource>
    suspend fun load(url: String): String?
    fun displayConfig(): DanmakuDisplayConfig
}
