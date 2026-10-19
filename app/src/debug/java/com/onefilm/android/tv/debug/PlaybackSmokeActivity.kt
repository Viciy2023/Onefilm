package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** Debug-only playback smoke test: real HTTP MP4 through media3 + SurfaceView. */
class PlaybackSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playerView = PlayerView(this)
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()
        playerView.postDelayed({ playerView.setRender(1) }, 4000)
        playerView.postDelayed({ playerView.setRender(0) }, 9000)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val URL = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4"
    }
}
