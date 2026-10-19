package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.github.assrender.AssExtractorsFactory
import io.github.assrender.AssHandler
import io.github.assrender.AssRenderersFactory
import io.github.assrender.SubtitleOverlayView

/** Debug-only libass verification: real MKV with an embedded ASS track. */
class AssSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val overlay = SubtitleOverlayView(this)
        val handler = AssHandler(this, overlay, 640, 360)
        player = ExoPlayer.Builder(this, AssRenderersFactory(this, handler)).build()
        handler.player = player
        val mediaSourceFactory = DefaultMediaSourceFactory(
            DefaultHttpDataSource.Factory(),
            AssExtractorsFactory(handler),
        )
        player.setMediaSource(mediaSourceFactory.createMediaSource(MediaItem.fromUri(URL)))
        val playerView = PlayerView(this)
        playerView.player = player
        playerView.addView(overlay, ViewGroup.LayoutParams(-1, -1))
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player.prepare()
        player.play()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val URL = "http://127.0.0.1:8000/sample.mkv"
    }
}

