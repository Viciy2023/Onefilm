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
    fun decodeText(): String
    fun tracks(): List<LegacyTrack>
    fun selectTrack(track: LegacyTrack)
    fun toggleRepeat()
    fun setTextOffset(ms: Long)
    fun setAudioOffset(ms: Long)
    fun textOffset(): Long
    fun audioOffset(): Long
    fun setDanmakuEnabled(enabled: Boolean)
    fun release()
}

interface LegacyPlayerFactory {
    fun create(): LegacyPlayer
}
