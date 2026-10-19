package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.ui.PlayerView

/** Debug-only: Exo subtitle offset via the forked TextRenderer. */
class SubtitleOffsetSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val offsetMs = intent.getLongExtra("offset", 0L)
        TextRenderer.setSubtitleOffsetMs(offsetMs)
        val playerView = PlayerView(this)
        playerView.useController = false
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val URL = "http://127.0.0.1:8000/sample-srt.mkv"
    }
}
