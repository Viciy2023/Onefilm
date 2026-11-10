package com.onefilm.android.tv.mobileui.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.feature.player.DanmakuController
import com.onefilm.android.tv.compose.feature.player.PlaybackSession

/**
 * 手机播放器的视频层：与 TV 使用同一套 PlaybackSession / PlayerManager（Exo + MPV），
 * 但不含任何 TV 的焦点、菜单或横屏布局。
 */
@Composable
fun MobileVideoSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val legacy = PlaybackSession.legacyPlayer
    val player = PlaybackSession.player
    val textureRender = PlaybackSession.textureRender
    val resizeMode = PlaybackSession.resizeMode
    val mpv = PlaybackSession.engineMpv
    val episode = PlaybackSession.currentEpisode
    val danmakuKey = "${PlaybackSession.siteKey}:${PlaybackSession.vodId}:${episode?.url.orEmpty()}"
    LaunchedEffect(danmakuKey, player) {
        if (player != null) {
            DanmakuController.ensureLoaded(context, danmakuKey, ComposeDependencies.danmakuDataSource)
        }
    }
    if (legacy == null || player == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_empty), color = Color.White)
        }
        return
    }
    key(mpv, textureRender) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val root = FrameLayout(ctx)
                val video = PlayerView(ctx).apply {
                    useController = false
                    setRender(if (textureRender) 1 else 0)
                    this.resizeMode = resizeMode
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
                root.addView(video, FrameLayout.LayoutParams(-1, -1))
                if (mpv) {
                    val surfaceView = SurfaceView(ctx)
                    surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            legacy.attachMpvSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            legacy.attachMpvSurface(null)
                        }
                    })
                    root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
                }
                val danmaku = DanmakuView(ctx)
                root.addView(danmaku, FrameLayout.LayoutParams(-1, -1))
                legacy.bindPlayerView(video)
                video.player = player
                DanmakuController.attach(danmaku, video, player, danmakuKey)
                root.tag = Triple(video, danmaku, mpv)
                root
            },
            update = { root ->
                val triple = root.tag as? Triple<*, *, *>
                val video = triple?.first as? PlayerView
                val danmaku = triple?.second as? DanmakuView
                video?.player = PlaybackSession.player
                video?.resizeMode = PlaybackSession.resizeMode
                video?.setRender(if (PlaybackSession.textureRender) 1 else 0)
                if (danmaku != null) {
                    if (!DanmakuController.attachedTo(danmaku)) {
                        DanmakuController.attach(danmaku, video, PlaybackSession.player, danmakuKey)
                    } else {
                        DanmakuController.setPlayer(PlaybackSession.player)
                    }
                }
            },
            onRelease = { root ->
                val triple = root.tag as? Triple<*, *, *>
                (triple?.first as? PlayerView)?.player = null
                (triple?.second as? DanmakuView)?.let { DanmakuController.detach(it) }
                legacy.attachMpvSurface(null)
            },
        )
    }
}
