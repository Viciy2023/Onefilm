package com.onefilm.android.tv.debug

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.onefilm.android.tv.player.media.ChapterLoader

/** Debug-only chapter loader verification using the same Range path as the app. */
class ChapterSmokeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.textSize = 18f
        text.setPadding(32, 64, 32, 32)
        setContentView(text)
        ChapterLoader.load(this, Uri.parse(URL)) { chapters ->
            val summary = chapters.joinToString("\n") { "${it.label} @ ${it.timeUs / 1000}ms" }
            text.text = "chapters=${chapters.size}\n\n$summary"
            Log.i(TAG, "chapters=${chapters.size}")
        }
    }

    private companion object {
        const val TAG = "ChapterSmoke"
        const val URL = "http://127.0.0.1:8000/sample-ch.mkv"
    }
}
