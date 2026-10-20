package com.onefilm.android.tv.composebridge

import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.Constant
import com.onefilm.android.tv.bean.Track
import com.onefilm.android.tv.compose.bridge.LegacyPlayer
import com.onefilm.android.tv.compose.bridge.LegacyPlayerListener
import com.onefilm.android.tv.compose.bridge.LegacyTrack
import com.onefilm.android.tv.player.PlayerManager
import com.onefilm.android.tv.player.media.PlaySpec
import com.onefilm.android.tv.player.track.TrackUtil
import com.onefilm.android.tv.setting.PlayerSetting

/** 包装旧 PlayerManager，Compose 只调用这里的稳定接口。 */
class AppLegacyPlayer : LegacyPlayer {

    private val manager: PlayerManager
    private var renderMode: Int = PlayerSetting.RENDER_SURFACE
    private var boundView: PlayerView? = null
    override var listener: LegacyPlayerListener? = null

    override val player: Player?
        get() = manager.player

    init {
        manager = PlayerManager(object : PlayerManager.Callback {
            override fun onPrepare() {
                listener?.onPrepare()
            }

            override fun onTracksChanged() {
                listener?.onTracksChanged()
            }

            override fun onDecodeChanged() {
                listener?.onDecodeChanged()
            }

            override fun onMediaOptionsChanged() {}

            override fun onError(msg: String) {
                listener?.onError(msg)
            }

            override fun onPlayerRebuild(newPlayer: Player?) {
                listener?.onPlayerRebuild(newPlayer)
            }

            override fun onDanmakuSourceChanged(uri: android.net.Uri?) {}
            override fun onDanmakuConfigChanged(config: androidx.media3.ui.danmaku.DanmakuConfig) {}
            override fun onDanmakuEnabledChanged(enabled: Boolean) {}
            override fun onDanmakuSent(text: String) {}
        })
    }

    override fun start(
        url: String,
        key: String,
        title: String,
        startMs: Long,
        headers: Map<String, String>,
    ) {
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        val spec = PlaySpec.from(key, url, headers.toMutableMap(), metadata).checkUa()
        manager.start(spec, Constant.TIMEOUT_PLAY, startMs)
    }

    override fun bindPlayerView(view: PlayerView) {
        boundView = view
        view.setRender(renderMode)
        manager.bindPlayerView(view)
    }

    override fun attachMpvSurface(surface: Surface?) {
        manager.attachMpvSurface(surface)
    }

    override fun isMpv(): Boolean = manager.isMpv

    override fun setEngine(mpv: Boolean) {
        manager.setEngine(if (mpv) PlayerSetting.ENGINE_MPV else PlayerSetting.ENGINE_EXO)
    }

    override fun setRender(mode: Int) {
        renderMode = mode
        boundView?.setRender(mode)
    }

    override fun toggleDecode() {
        manager.toggleDecode()
    }

    override fun decodeText(): String = manager.decodeText

    override fun tracks(): List<LegacyTrack> {
        val tracks = manager.currentTracks ?: return emptyList()
        val result = ArrayList<LegacyTrack>()
        tracks.groups.forEach { group: Tracks.Group ->
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                result.add(
                    LegacyTrack(
                        type = group.type,
                        name = format.label ?: format.language ?: format.id ?: "轨道 ${i + 1}",
                        format = TrackUtil.describeFormat(format),
                        selected = group.isTrackSelected(i),
                    ),
                )
            }
        }
        return result
    }

    override fun selectTrack(track: LegacyTrack) {
        val item = Track(track.type, track.name, track.format)
        item.isSelected = track.selected
        item.key(manager.key ?: "")
        manager.setTrack(item)
    }

    override fun toggleRepeat() {
        manager.setRepeatOne(!manager.isRepeatOne)
    }

    override fun setTextOffset(ms: Long) = manager.setTextOffsetMs(ms)

    override fun setAudioOffset(ms: Long) = manager.setAudioOffsetMs(ms)

    override fun textOffset(): Long = manager.textOffsetMs

    override fun audioOffset(): Long = manager.audioOffsetMs

    override fun setDanmakuEnabled(enabled: Boolean) = manager.setDanmakuEnabled(enabled)

    override fun release() {
        manager.release()
    }
}
