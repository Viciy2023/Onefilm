package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.media3.common.MediaChapter
import com.onefilm.android.tv.player.media.MkvChapterParser
import okhttp3.OkHttpClient
import okhttp3.Request

/** Debug-only chapter parsing verification against an MKV served over adb reverse. */
class ChapterSmokeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.textSize = 18f
        text.setPadding(32, 64, 32, 32)
        setContentView(text)
        Thread {
            try {
                val client = OkHttpClient()
                val bytes = client.newCall(Request.Builder().url(URL).build()).execute().use { response ->
                    response.body?.bytes() ?: ByteArray(0)
                }
                val chapters: List<MediaChapter> = MkvChapterParser.parse(bytes)
                val summary = chapters.joinToString("\n") { "${it.label} @ ${it.timeUs / 1000}ms" }
                Log.i(TAG, "bytes=${bytes.size} chapters=${chapters.size}\n$summary")
                runOnUiThread {
                    text.text = "chapters=${chapters.size}\n\n$summary"
                }
            } catch (t: Throwable) {
                Log.e(TAG, "parse failed", t)
                runOnUiThread { text.text = "parse failed: ${t.message}" }
            }
        }.start()
    }

    private companion object {
        const val TAG = "ChapterSmoke"
        const val URL = "http://127.0.0.1:8000/sample-ch.mkv"
    }
}
