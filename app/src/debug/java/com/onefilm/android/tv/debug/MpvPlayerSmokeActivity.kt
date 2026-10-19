package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.mpvplayer.MpvPlayer
import com.onefilm.android.tv.player.mpv.MpvRuntimeClient

/** Debug-only: MpvPlayer adapter with a manually attached SurfaceView. */
class MpvPlayerSmokeActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var client: MpvRuntimeClient
    private lateinit var player: MpvPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        surfaceView = SurfaceView(this).apply { holder.addCallback(this@MpvPlayerSmokeActivity) }
        status = TextView(this).apply { textSize = 16f; setPadding(24, 24, 24, 24) }
        root.addView(surfaceView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        client = MpvRuntimeClient(this, null)
        client.bind()
        player = MpvPlayer.Builder(this).setRuntimeBridge(client).build()
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()

        handler.postDelayed({
            player.pause()
            Log.i(TAG, "paused state=${player.playbackState} playWhenReady=${player.playWhenReady} pos=${player.currentPosition}")
        }, 2000)
        handler.postDelayed({
            player.play()
            Log.i(TAG, "resumed state=${player.playbackState} playWhenReady=${player.playWhenReady}")
        }, 4000)
        handler.postDelayed({
            Log.i(TAG, "before seek state=${player.playbackState} pos=${player.currentPosition} dur=${player.duration}")
            player.seekTo(3000)
        }, 6000)
        handler.postDelayed({
            Log.i(TAG, "after seek state=${player.playbackState} pos=${player.currentPosition} dur=${player.duration}")
        }, 6800)
        handler.postDelayed({
            player.stop()
        }, 8000)
        handler.postDelayed({
            Log.i(TAG, "after stop state=${player.playbackState}")
        }, 9000)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.i(TAG, "surfaceCreated valid=${holder.surface.isValid}")
        player.attachSurface(holder.surface)
        status.text = "surface attached"
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) = player.attachSurface(null)

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
