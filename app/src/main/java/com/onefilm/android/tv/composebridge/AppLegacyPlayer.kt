package com.onefilm.android.tv.composebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Surface
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.App
import com.onefilm.android.tv.Constant
import com.onefilm.android.tv.bean.Track
import com.onefilm.android.tv.compose.bridge.LegacyPlayer
import com.onefilm.android.tv.compose.bridge.LegacyPlayerListener
import com.onefilm.android.tv.compose.bridge.LegacyTrack
import com.onefilm.android.tv.player.PlayerManager
import com.onefilm.android.tv.player.media.PlaySpec
import com.onefilm.android.tv.player.track.TrackUtil
import com.onefilm.android.tv.service.PlaybackService
import com.onefilm.android.tv.setting.PlayerSetting

/** 包装旧 PlayerManager，优先复用 PlaybackService 的实例以保留 MediaSession/后台播放。 */
class AppLegacyPlayer : LegacyPlayer {

    private data class PendingStart(
        val url: String,
        val key: String,
        val title: String,
        val startMs: Long,
        val headers: Map<String, String>,
    )

    private val context: Context = App.get()
    private var manager: PlayerManager? = null
    private var service: PlaybackService? = null
    private var pendingStart: PendingStart? = null
    private var pendingSurface: Surface? = null
    private var boundView: PlayerView? = null
    private var renderMode: Int = PlayerSetting.RENDER_SURFACE
    private var standalone = false
    override var listener: LegacyPlayerListener? = null

    override val player: Player?
        get() = manager?.player

    private val standaloneCallback = object : PlayerManager.Callback {
        override fun onPrepare() = listener?.onPrepare() ?: Unit
        override fun onTracksChanged() = listener?.onTracksChanged() ?: Unit
        override fun onDecodeChanged() = listener?.onDecodeChanged() ?: Unit
        override fun onMediaOptionsChanged() = Unit
        override fun onError(msg: String) = listener?.onError(msg) ?: Unit
        override fun onPlayerRebuild(newPlayer: Player?) = listener?.onPlayerRebuild(newPlayer) ?: Unit
        override fun onDanmakuSourceChanged(uri: android.net.Uri?) = Unit
        override fun onDanmakuConfigChanged(config: androidx.media3.ui.danmaku.DanmakuConfig) = Unit
        override fun onDanmakuEnabledChanged(enabled: Boolean) = Unit
        override fun onDanmakuSent(text: String) = Unit
    }

    private val serviceCallback = object : PlaybackService.PlayerCallback {
        override fun onPrepare() { listener?.onPrepare() }
        override fun onTracksChanged() { listener?.onTracksChanged() }
        override fun onDecodeChanged() { listener?.onDecodeChanged() }
        override fun onMediaOptionsChanged() {}
        override fun onError(msg: String) { listener?.onError(msg) }
        override fun onPlayerRebuild(newPlayer: Player?) { listener?.onPlayerRebuild(newPlayer) }
        override fun onDanmakuSourceChanged(uri: android.net.Uri?) {}
        override fun onDanmakuConfigChanged(config: androidx.media3.ui.danmaku.DanmakuConfig) {}
        override fun onDanmakuEnabledChanged(enabled: Boolean) {}
        override fun onDanmakuSent(text: String) {}
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val local = binder as? PlaybackService.LocalBinder ?: return
            val svc = local.service
            service = svc
            manager = svc.player()
            svc.addPlayerCallback(serviceCallback)
            boundView?.let { view ->
                view.setRender(renderMode)
                manager?.bindPlayerView(view)
            }
            pendingSurface?.let { manager?.attachMpvSurface(it) }
            listener?.onPlayerRebuild(manager?.player)
            pendingStart?.let { startInternal(it) }
            pendingStart = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            manager = null
            listener?.onPlayerRebuild(null)
        }
    }

    init {
        val bound = runCatching {
            context.bindService(
                Intent(context, PlaybackService::class.java).setAction(PlaybackService.LOCAL_BIND_ACTION),
                connection,
                Context.BIND_AUTO_CREATE,
            )
        }.getOrDefault(false)
        if (!bound) fallbackStandalone()
        // 服务极少数情况下无法连接时，退回独立 PlayerManager，保证播放可用。
        Handler(Looper.getMainLooper()).postDelayed({
            if (manager == null) fallbackStandalone()
        }, 2_000L)
    }

    private fun fallbackStandalone() {
        if (manager != null) return
        standalone = true
        manager = PlayerManager(standaloneCallback)
        listener?.onPlayerRebuild(manager?.player)
        pendingStart?.let { startInternal(it) }
        pendingStart = null
    }

    override fun start(
        url: String,
        key: String,
        title: String,
        startMs: Long,
        headers: Map<String, String>,
    ) {
        val pending = PendingStart(url, key, title, startMs, headers)
        if (manager != null) startInternal(pending) else pendingStart = pending
    }

    private fun startInternal(pending: PendingStart) {
        val metadata = MediaMetadata.Builder().setTitle(pending.title).build()
        val spec = PlaySpec.from(
            pending.key,
            pending.url,
            pending.headers.toMutableMap(),
            metadata,
        ).checkUa()
        manager?.start(spec, Constant.TIMEOUT_PLAY, pending.startMs)
    }

    override fun bindPlayerView(view: PlayerView) {
        boundView = view
        view.setRender(renderMode)
        manager?.bindPlayerView(view)
    }

    override fun attachMpvSurface(surface: Surface?) {
        pendingSurface = surface
        manager?.attachMpvSurface(surface)
    }

    override fun isMpv(): Boolean = manager?.isMpv == true

    override fun setEngine(mpv: Boolean) {
        manager?.setEngine(if (mpv) PlayerSetting.ENGINE_MPV else PlayerSetting.ENGINE_EXO)
    }

    override fun setRender(mode: Int) {
        renderMode = mode
        boundView?.setRender(mode)
    }

    override fun toggleDecode() {
        manager?.toggleDecode()
    }

    override fun decodeText(): String = manager?.decodeText ?: "硬解"

    override fun tracks(): List<LegacyTrack> {
        val tracks = manager?.currentTracks ?: return emptyList()
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
        item.key(manager?.key ?: "")
        manager?.setTrack(item)
    }

    override fun toggleRepeat() {
        manager?.let { it.setRepeatOne(!it.isRepeatOne) }
    }

    override fun setTextOffset(ms: Long) {
        manager?.setTextOffsetMs(ms)
    }

    override fun setAudioOffset(ms: Long) {
        manager?.setAudioOffsetMs(ms)
    }

    override fun textOffset(): Long = manager?.textOffsetMs ?: 0L

    override fun audioOffset(): Long = manager?.audioOffsetMs ?: 0L

    override fun setDanmakuEnabled(enabled: Boolean) {
        manager?.setDanmakuEnabled(enabled)
    }

    override fun preload(url: String, key: String, title: String) {
        val metadata = MediaMetadata.Builder().setTitle(title).build()
        val spec = PlaySpec.from(key, url, mutableMapOf(), metadata).checkUa()
        manager?.preload(spec, 0L)
    }

    override fun release() {
        runCatching { context.unbindService(connection) }
        if (standalone) manager?.release()
        manager = null
        service = null
        pendingStart = null
        boundView = null
    }
}
