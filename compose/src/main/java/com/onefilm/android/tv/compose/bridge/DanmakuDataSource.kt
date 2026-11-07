package com.onefilm.android.tv.compose.bridge

import androidx.media3.ui.danmaku.DanmakuConfig

data class DanmakuSource(val name: String, val url: String)

data class DanmakuDisplayConfig(
    val show: Boolean,
    val scale: Float,
    val transparency: Float,
    val durationMs: Long,
    val lines: Int,
    val blockWords: List<String>,
)

/** 旧版弹幕接口桥接：搜索弹幕源并下载弹幕文件内容。 */
interface DanmakuDataSource {
    suspend fun search(name: String, episode: String): List<DanmakuSource>
    suspend fun load(url: String): String?

    /** 播放器快捷开关所需的少量字段。 */
    fun displayConfig(): DanmakuDisplayConfig

    /** 完整显示配置，与手机/旧播放页共用 DanmakuSetting。 */
    fun engineConfig(): DanmakuConfig
}
