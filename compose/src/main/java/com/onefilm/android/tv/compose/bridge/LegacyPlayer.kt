package com.onefilm.android.tv.compose.bridge

import android.view.Surface
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView

data class LegacyTrack(
    val type: Int,
    val name: String,
    val format: String,
    val selected: Boolean,
)

/** 媒体章节（MKV/MP4 内嵌）。timeMs 为 -1 表示没有时间点。 */
data class LegacyChapter(
    val label: String,
    val timeMs: Long,
    val selected: Boolean,
)

/** 媒体版次（同一影片的多版本）。durationMs 为 -1 表示未知。 */
data class LegacyEdition(
    val label: String,
    val durationMs: Long,
    val selected: Boolean,
)

interface LegacyPlayerListener {
    fun onPrepare() {}
    fun onTracksChanged() {}
    fun onDecodeChanged() {}
    fun onError(message: String) {}
    fun onPlayerRebuild(player: Player?) {}
}

/** 旧 PlayerManager 的 Compose 桥接，播放内核仍然只保留一份。 */
interface LegacyPlayer {
    val player: Player?
    var listener: LegacyPlayerListener?
    fun start(
        url: String,
        key: String,
        title: String,
        startMs: Long,
        headers: Map<String, String> = emptyMap(),
    )

    fun bindPlayerView(view: PlayerView)
    fun attachMpvSurface(surface: Surface?)
    fun isMpv(): Boolean
    fun setEngine(mpv: Boolean)
    fun setRender(mode: Int)
    fun toggleDecode()
    /** 是否走软解；与展示文案无关，供逻辑判断。 */
    fun isSoftwareDecode(): Boolean = false
    fun decodeText(): String
    fun tracks(): List<LegacyTrack>
    fun selectTrack(track: LegacyTrack)

    /** 当前媒体的章节列表；不支持时为空。 */
    fun chapters(): List<LegacyChapter> = emptyList()
    /** 按索引选择章节（跳转到章节起点）。 */
    fun selectChapter(index: Int) {}
    /** 当前媒体的版次列表；不支持时为空。 */
    fun editions(): List<LegacyEdition> = emptyList()
    /** 按索引选择版次。 */
    fun selectEdition(index: Int) {}
    fun toggleRepeat()
    fun setTextOffset(ms: Long)
    fun setAudioOffset(ms: Long)
    fun textOffset(): Long
    fun audioOffset(): Long
    fun setDanmakuEnabled(enabled: Boolean)
    fun preload(url: String, key: String, title: String)

    /** 挂载外挂字幕（本地路径或远程 http 地址）。 */
    fun addSubtitle(name: String, url: String, lang: String) {}
    fun subtitleScale(): Float
    fun cycleSubtitleScale()
    fun subtitlePosition(): Float
    fun cycleSubtitlePosition()
    fun subtitleColorLabel(): String
    fun cycleSubtitleColor()
    fun release()
}

interface LegacyPlayerFactory {
    fun create(): LegacyPlayer
}
