package com.onefilm.android.tv.composebridge

import android.app.Activity
import android.content.Context
import com.onefilm.android.tv.compose.bridge.DetailLauncher
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.ui.activity.VideoActivity

class AppDetailLauncher : DetailLauncher {
    override fun open(context: Context, card: MediaCard) {
        if (context !is Activity) return
        VideoActivity.start(context, card.key, card.id, card.title, card.posterUrl ?: "")
    }

    override fun openEpisode(context: Context, card: MediaCard, mark: String) {
        if (context !is Activity) return
        VideoActivity.start(context, card.key, card.id, card.title, card.posterUrl ?: "", mark)
    }
}
