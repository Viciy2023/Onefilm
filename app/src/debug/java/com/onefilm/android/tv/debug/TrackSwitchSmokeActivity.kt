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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.mpvplayer.MpvPlayer
import com.onefilm.android.tv.bean.Track
import com.onefilm.android.tv.player.mpv.MpvRuntimeClient
import com.onefilm.android.tv.player.track.TrackUtil

/**
 * Debug-only: exercises the same TrackUtil/Track path the track dialog uses, against MPV.
 * Verifies audio switching, subtitle switching and turning subtitles off.
 */
class TrackSwitchSmokeActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var client: MpvRuntimeClient
    private lateinit var player: MpvPlayer
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        surfaceView = SurfaceView(this).apply { holder.addCallback(this@TrackSwitchSmokeActivity) }
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

        handler.postDelayed({ selectAudio() }, 3000)
        handler.postDelayed({ dumpState("after-audio") }, 4000)
        handler.postDelayed({ selectSubtitle() }, 4500)
        handler.postDelayed({ dumpState("after-sub") }, 5500)
        handler.postDelayed({ switchSubtitleOff() }, 6500)
        handler.postDelayed({ dumpState("final") }, 8000)
        handler.postDelayed({ player.stop() }, 9000)
    }

    private fun dumpState(tag: String) {
        Log.i(TAG, "[$tag] aid=${client.audioTrack} sid=${client.subtitleTrack}")
        Log.i(TAG, "[$tag] audio=${client.audioTrackDetails.joinToString(" | ")}")
        Log.i(TAG, "[$tag] subs=${client.subtitleTrackDetails.joinToString(" | ")}")
        player.currentTracks.groups.forEachIndexed { gi, group ->
            Log.i(TAG, "[$tag] g$gi type=${group.type} selected=${group.isSelected}")
            for (i in 0 until group.length) {
                Log.i(TAG, "[$tag]   [$i] sel=${group.isTrackSelected(i)} ${TrackUtil.describeFormat(group.getTrackFormat(i))}")
            }
        }
    }

    private fun trackOf(type: Int, flatIndex: Int, selected: Boolean): Track? {
        var seen = 0
        for (group in player.currentTracks.groups) {
            if (group.type != type) continue
            for (i in 0 until group.length) {
                if (seen == flatIndex) {
                    return Track(type, "smoke", TrackUtil.describeFormat(group.getTrackFormat(i))).apply {
                        isSelected = selected
                    }
                }
                seen++
            }
        }
        return null
    }

    private fun selectAudio() {
        dumpState("before-audio")
        val track = trackOf(C.TRACK_TYPE_AUDIO, 1, true) ?: return
        Log.i(TAG, "audio track fmt=${track.format} selected=${track.isSelected}")
        TrackUtil.setTrackSelection(player, track)
        Log.i(TAG, "audio params overrides=${player.trackSelectionParameters.overrides.size}")
    }

    private fun selectSubtitle() {
        val track = trackOf(C.TRACK_TYPE_TEXT, 1, true) ?: return
        Log.i(TAG, "sub track fmt=${track.format} selected=${track.isSelected}")
        TrackUtil.setTrackSelection(player, track)
        Log.i(TAG, "sub params overrides=${player.trackSelectionParameters.overrides.size}")
    }

    private fun switchSubtitleOff() {
        val group = player.currentTracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_TEXT && (0 until it.length).any { i -> it.isTrackSelected(i) }
        } ?: return
        val selected = (0 until group.length).first { group.isTrackSelected(it) }
        // TrackAdapter calls toggle() before onItemClick, so a selected row arrives with false.
        val track = Track(C.TRACK_TYPE_TEXT, "smoke", TrackUtil.describeFormat(group.getTrackFormat(selected))).apply {
            isSelected = false
        }
        Log.i(TAG, "off fmt=${track.format} selected=${track.isSelected}")
        TrackUtil.setTrackSelection(player, track)
        Log.i(TAG, "off params overrides=${player.trackSelectionParameters.overrides.size}")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
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
        const val TAG = "TrackSwitchSmoke"
        const val URL = "http://127.0.0.1:8000/sample-tracks.mkv"
    }
}
