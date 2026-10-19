package com.onefilm.android.tv.debug

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.player.media.ChapterLoader

/** Debug-only: plays an MKV, lists parsed chapters, seeks on click. */
class ChapterSmokeActivity : Activity() {

    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val playerView = PlayerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(playerView)
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(URL))
        player.prepare()
        player.play()

        ChapterLoader.load(this, Uri.parse(URL)) { chapters ->
            chapters.forEach { chapter ->
                val button = Button(this).apply {
                    text = "${chapter.label} @ ${chapter.timeUs / 1000}ms"
                    setOnClickListener {
                        player.seekTo(chapter.timeUs / 1000)
                        Log.i(TAG, "seek ${chapter.label} target=${chapter.timeUs / 1000} pos=${player.currentPosition}")
                    }
                }
                list.addView(button)
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ChapterSmoke"
        const val URL = "http://127.0.0.1:8000/sample-ch.mkv"
    }
}
