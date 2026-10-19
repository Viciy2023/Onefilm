package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.mpvplayer.MpvPlayer
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.player.mpv.MpvRuntimeClient

/** Debug-only: media3 Player adapter on top of the MPV runtime. */
class MpvPlayerSmokeActivity : Activity() {

    private lateinit var client: MpvRuntimeClient
    private lateinit var player: MpvPlayer
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        client = MpvRuntimeClient(this, null)
        client.bind()
        player = MpvPlayer.Builder(this).setRuntimeBridge(client).build()
        val playerView = PlayerView(this)
        playerView.player = player
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()

        handler.postDelayed({
            Log.i(TAG, "before seek state=${player.playbackState} pos=${player.currentPosition} dur=${player.duration}")
            player.seekTo(3000)
        }, 6000)
        handler.postDelayed({
            Log.i(TAG, "after seek state=${player.playbackState} pos=${player.currentPosition} dur=${player.duration}")
        }, 9000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        client.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "MpvPlayerSmoke"
        const val URL = "http://127.0.0.1:8000/sample-ch.mkv"
    }
}
