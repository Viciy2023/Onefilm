package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.bean.Track
import com.onefilm.android.tv.player.track.TrackUtil

/**
 * Debug-only: exercises the same TrackUtil/Track path the track dialog uses, against ExoPlayer.
 */
class ExoTrackSwitchSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playerView = PlayerView(this).apply { useController = false }
        setContentView(playerView, ViewGroup.LayoutParams(-1, -1))
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()

        handler.postDelayed({ dumpState("before-audio") }, 2500)
        handler.postDelayed({ selectAudio() }, 3000)
        handler.postDelayed({ dumpState("after-audio") }, 4000)
        handler.postDelayed({ selectSubtitle() }, 4500)
        handler.postDelayed({ dumpState("after-sub") }, 5500)
        handler.postDelayed({ switchSubtitleOff() }, 6000)
        handler.postDelayed({ dumpState("final") }, 7000)
    }

    private fun dumpState(tag: String) {
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
        val track = trackOf(C.TRACK_TYPE_AUDIO, 1, true) ?: return
        Log.i(TAG, "select audio fmt=${track.format}")
        TrackUtil.setTrackSelection(player, track)
    }

    private fun selectSubtitle() {
        val track = trackOf(C.TRACK_TYPE_TEXT, 1, true) ?: return
        Log.i(TAG, "select subtitle fmt=${track.format}")
        TrackUtil.setTrackSelection(player, track)
    }

    private fun switchSubtitleOff() {
        val group = player.currentTracks.groups.firstOrNull {
            it.type == C.TRACK_TYPE_TEXT && (0 until it.length).any { i -> it.isTrackSelected(i) }
        } ?: return
        val selected = (0 until group.length).first { group.isTrackSelected(it) }
        val track = Track(C.TRACK_TYPE_TEXT, "smoke", TrackUtil.describeFormat(group.getTrackFormat(selected))).apply {
            isSelected = false
        }
        Log.i(TAG, "toggle off fmt=${track.format}")
        TrackUtil.setTrackSelection(player, track)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ExoTrackSwitchSmoke"
        const val URL = "http://127.0.0.1:8000/sample-tracks.mkv"
    }
}
