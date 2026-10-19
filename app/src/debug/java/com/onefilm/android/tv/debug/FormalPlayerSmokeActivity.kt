package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import com.onefilm.android.tv.ui.activity.VideoActivity

/** Debug-only: opens the real VideoActivity with a direct URL so UI automation can drive it. */
class FormalPlayerSmokeActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val key = intent.getStringExtra("key")
        if (key != null) {
            VideoActivity.start(
                this,
                key,
                intent.getStringExtra("id") ?: "1",
                intent.getStringExtra("name") ?: "Preload Test",
            )
        } else {
            VideoActivity.start(this, intent.getStringExtra("url") ?: URL)
        }
        finish()
    }

    private companion object {
        const val URL = "http://127.0.0.1:8000/sample-tracks.mkv"
    }
}
