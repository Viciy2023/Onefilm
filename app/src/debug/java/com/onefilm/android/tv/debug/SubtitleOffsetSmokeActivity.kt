package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.ui.PlayerView

/** Debug-only: Exo subtitle offset via the forked TextRenderer. */
class SubtitleOffsetSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val offsetMs = intent.getLongExtra("offset", 0L)
        val dvPolicy = intent.getIntExtra("dv", -1)
        if (dvPolicy >= 0) {
            MediaCodecVideoRenderer.setDolbyVisionOutputPolicy(dvPolicy)
            android.util.Log.i("DolbyVisionPolicy", "applied=" + MediaCodecVideoRenderer.getDolbyVisionOutputPolicy())
        }
        TextRenderer.setSubtitleOffsetMs(offsetMs)
        TextRenderer.debugCueLogging = true
        android.util.Log.i("SubtitleOffset", "start elapsed=" + android.os.SystemClock.elapsedRealtime() + " offset=" + offsetMs)
        val playerView = PlayerView(this)
        playerView.useController = false
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()
        android.os.Handler(mainLooper).postDelayed({
            val groups = player.currentTracks.groups
            android.util.Log.i("SubtitleOffset", "groups=" + groups.size)
            groups.forEachIndexed { i, g ->
                android.util.Log.i("SubtitleOffset", "g" + i + " type=" + g.type + " selected=" + g.isSelected + " len=" + g.length)
            }
            val text = groups.indexOfFirst { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
            if (text >= 0) {
                val group = groups[text].mediaTrackGroup
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
                    .addOverride(androidx.media3.common.TrackSelectionOverride(group, 0))
                    .build()
                android.util.Log.i("SubtitleOffset", "selected text group " + text)
            }
        }, 1500)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val URL = "http://127.0.0.1:8000/sample-srt.mkv"
    }
}
