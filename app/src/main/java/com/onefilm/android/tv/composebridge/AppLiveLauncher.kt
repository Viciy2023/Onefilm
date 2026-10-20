package com.onefilm.android.tv.composebridge

import android.content.Context
import com.onefilm.android.tv.compose.bridge.LiveLauncher

class AppLiveLauncher : LiveLauncher {
    override fun open(context: Context) {
        runCatching {
            val clazz = Class.forName("com.onefilm.android.tv.ui.activity.LiveActivity")
            clazz.getMethod("start", Context::class.java).invoke(null, context)
        }
    }
}
