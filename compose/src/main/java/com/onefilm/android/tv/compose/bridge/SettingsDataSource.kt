package com.onefilm.android.tv.compose.bridge

import android.graphics.Bitmap

data class SettingsSnapshot(
    val engineMpv: Boolean,
    val background: Int,
    val bufferSeconds: Int,
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
)

data class ConfigLine(
    val name: String,
    val url: String,
    val current: Boolean,
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
    fun cycleDoh()
    fun setIncognito(enabled: Boolean)
    fun configPushUrl(): String
    fun qrBitmap(content: String, size: Int): Bitmap?
    fun setThemeMode(mode: Int)
}
