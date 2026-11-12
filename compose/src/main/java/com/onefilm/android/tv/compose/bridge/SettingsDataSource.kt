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
    val danmakuLoad: Boolean = false,
    val danmakuAuto: Boolean = false,
    val danmakuApi: String = "",
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
    val posterStyle: Int = 0,
    val cacheDays: Int,
    val cacheSizeLabel: String,
    val wallpaperLabel: String,
    val forceUpdate: Boolean,
    /** 「检查更新」总开关，默认开。 */
    val updateCheck: Boolean = true,
    val searchThreads: Int = 16,
    val adblock: Boolean = true,
    val danmakuStyle: Int = 1,
    val danmakuColorMode: Int = 0,
    val danmakuBold: Boolean = true,
    val danmakuTopLines: Int = 3,
    val danmakuBottomLines: Int = 3,
    val danmakuTimeOffsetMs: Long = 0L,
    val danmakuMaxOnScreen: Int = 100,
)

/** 搜索线程候选值，设置页与搜索实现共用。 */
val SEARCH_THREAD_OPTIONS = listOf(12, 16, 24, 36, 48)

/** 弹幕接口探活结果：ok 表示连接可用，detail 是版本号 / HTTP 状态 / 异常摘要。 */
data class DanmakuProbeResult(val ok: Boolean, val detail: String)

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
    /** 0=未探测 1=单仓 2=多仓，仅用于「配置」列表的标签。 */
    val kind: Int = 0,
)

/** Compose 设置页与旧 *Setting 类的桥接。 */
interface SettingsDataSource {
    fun load(): SettingsSnapshot

    /** 服务端最新版本号；失败返回空串。 */
    suspend fun latestVersion(): String = ""

    /** 缓存目录大小可能很大，后台计算，避免打开设置时阻塞主线程。 */
    suspend fun refreshCacheSize(): String = ""

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
    fun setDanmakuLoad(enabled: Boolean)
    fun setDanmakuAuto(enabled: Boolean)
    fun setDanmakuApi(url: String)

    /** 探活弹幕接口：先试 /api/config，再按真实请求方式发一次极轻量搜索。 */
    suspend fun probeDanmakuApi(url: String): DanmakuProbeResult = DanmakuProbeResult(false, "")

    /** 倍速预设（来自 SpeedSetting），播放器菜单循环使用。 */
    fun speedPresets(): FloatArray = floatArrayOf(0.5f, 0.8f, 1f, 1.2f, 1.5f, 2f, 3f, 5f)
    fun setDanmakuScale(scale: Float)
    fun setDanmakuTransparency(value: Float)
    fun setDanmakuDuration(ms: Long)
    fun setDanmakuLines(lines: Int)
    fun setDanmakuStyle(mode: Int)
    fun setDanmakuColorMode(mode: Int)
    fun setDanmakuBold(bold: Boolean)
    fun setDanmakuTopLines(lines: Int)
    fun setDanmakuBottomLines(lines: Int)
    fun setDanmakuTimeOffsetMs(offset: Long)
    fun setDanmakuMaxOnScreen(value: Int)

    fun setPreloadEnabled(enabled: Boolean)
    fun setPreloadNext(enabled: Boolean)
    fun setPreloadThreads(threads: Int)
    fun setPreloadSizeMb(sizeMb: Int)

    fun resetUa()
    /** 设置自定义 UA；空串表示恢复默认。 */
    fun setUa(ua: String) {}
    /** 后台播放模式：0=关，1=后台播放，2=画中画。 */
    fun backgroundMode(): Int = 2

    /** 播放时的 m3u8 广告过滤开关。 */
    fun setAdblock(enabled: Boolean) {}

    fun loadConfig(
        type: Int,
        url: String,
        name: String = "",
        onResult: (Boolean, String) -> Unit,
    )
    fun configLines(type: Int): List<ConfigLine>
    fun allConfigLines(): List<ConfigLine>
    fun deleteConfigLine(type: Int, url: String)

    /** 后台探测未加载过的接口是单仓还是多仓（用于「配置」列表标签）。 */
    suspend fun detectConfigKinds() {}

    /** 当前多仓（仓库）名称；不是多仓时为空串。 */
    fun depotName(): String = ""

    /** 当前仓库包含的线路；不是多仓时为空列表。 */
    fun depotLines(): List<ConfigLine> = emptyList()

    /** 切换仓库里的某条线路（等价于加载这条线路的接口配置）。 */
    fun selectDepotLine(url: String, name: String, onResult: (Boolean, String) -> Unit) {
        loadConfig(0, url, name, onResult)
    }
    fun cycleDoh()
    fun cyclePlayTimeout()
    fun setIncognito(enabled: Boolean)
    fun setForceUpdate(enabled: Boolean)

    /** 「检查更新」总开关：关掉后彻底不再检查版本。 */
    fun setUpdateCheck(enabled: Boolean) {}
    fun configPushUrl(): String
    fun qrBitmap(content: String, size: Int): Bitmap?
    fun setThemeMode(mode: Int)
    fun setPosterStyle(style: Int)
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
