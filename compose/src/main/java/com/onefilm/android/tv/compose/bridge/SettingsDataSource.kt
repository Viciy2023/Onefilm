package com.onefilm.android.tv.compose.bridge

import android.graphics.Bitmap

data class SettingsSnapshot(
    val engineMpv: Boolean,
    val background: Int,
    val bufferSeconds: Int,
    val playTimeoutSeconds: Int,
    val renderTexture: Boolean,
    val tunnel: Boolean,
    val audioPassThrough: Boolean,
    val audioPrefer: Boolean,
    val videoPrefer: Boolean,
    val dvPolicyLabel: String,
    val subtitleStyle: Int,
    val subtitleScale: Float,
    val subtitlePosition: Float,
    val subtitleColorLabel: String,
    val subtitleEdgeWidth: Float,
    val danmakuBlockWords: String,
    val danmakuShow: Boolean,
    val danmakuScale: Float,
    val danmakuTransparency: Float,
    val danmakuDurationMs: Long,
    val danmakuLines: Int,
    val preloadEnabled: Boolean,
    val preloadNext: Boolean,
    val preloadThreads: Int,
    val preloadSizeMb: Int,
    val ua: String,
    val version: String,
    val configUrl: String,
    val liveConfigUrl: String,
    val wallConfigUrl: String,
    val incognito: Boolean,
    val dohLabel: String,
    val themeMode: Int,
    val cacheDays: Int,
    val cacheSizeLabel: String,
    val wallpaperLabel: String,
    val forceUpdate: Boolean,
    val searchThreads: Int = 16,
)

/** 搜索线程候选值，设置页与搜索实现共用。 */
val SEARCH_THREAD_OPTIONS = listOf(12, 16, 24, 36, 48)

/** 搜索源勾选项。 */
data class SearchSiteItem(
    val key: String,
    val name: String,
    val enabled: Boolean,
)

data class ConfigLine(
    val name: String,
    val url: String,
    val current: Boolean,
    val type: Int = 0,
)

/** Compose 设置页与旧 *Setting 类的桥接。 */
interface SettingsDataSource {
    fun load(): SettingsSnapshot

    fun setEngine(mpv: Boolean)
    fun setBackground(mode: Int)
    fun setBufferSeconds(seconds: Int)
    fun setRenderTexture(texture: Boolean)
    fun setTunnel(enabled: Boolean)
    fun setAudioPassThrough(enabled: Boolean)
    fun setAudioPrefer(enabled: Boolean)
    fun setVideoPrefer(enabled: Boolean)
    fun cycleDolbyVisionPolicy()

    fun setSubtitleStyle(style: Int)
    fun setSubtitleScale(scale: Float)
    fun setSubtitlePosition(position: Float)
    fun setSubtitleEdgeWidth(width: Float)
    fun setDanmakuBlockWords(words: String)
    fun cycleSubtitleColor()

    fun setDanmakuShow(show: Boolean)
    fun setDanmakuScale(scale: Float)
    fun setDanmakuTransparency(value: Float)
    fun setDanmakuDuration(ms: Long)
    fun setDanmakuLines(lines: Int)

    fun setPreloadEnabled(enabled: Boolean)
    fun setPreloadNext(enabled: Boolean)
    fun setPreloadThreads(threads: Int)
    fun setPreloadSizeMb(sizeMb: Int)

    fun resetUa()

    fun loadConfig(
        type: Int,
        url: String,
        name: String = "",
        onResult: (Boolean, String) -> Unit,
    )
    fun configLines(type: Int): List<ConfigLine>
    fun allConfigLines(): List<ConfigLine>
    fun deleteConfigLine(type: Int, url: String)
    fun cycleDoh()
    fun cyclePlayTimeout()
    fun setIncognito(enabled: Boolean)
    fun setForceUpdate(enabled: Boolean)
    fun configPushUrl(): String
    fun qrBitmap(content: String, size: Int): Bitmap?
    fun setThemeMode(mode: Int)
    fun setCacheDays(days: Int)
    fun clearCache()
    fun cycleWallpaper()

    /** 搜索线程数（12/16/24/36/48）。 */
    fun setSearchThreads(threads: Int) {}

    /** 当前点播接口下所有可搜索站点，按配置顺序返回。 */
    fun searchSites(): List<SearchSiteItem> = emptyList()

    /** 勾选/取消单个站点是否参与搜索。 */
    fun setSearchSiteEnabled(key: String, enabled: Boolean) {}

    /** 一次勾选/取消全部站点。 */
    fun setAllSearchSites(enabled: Boolean) {}

    /** 导出诊断信息（环境 + 最近一次崩溃）到 Download 目录，返回路径；失败返回空串。 */
    fun exportDiagnostics(): String = ""

    /** 首次启动是否展示存储权限说明。 */
    fun shouldShowStorageIntro(): Boolean = false

    fun markStorageIntroDone() {}
}
