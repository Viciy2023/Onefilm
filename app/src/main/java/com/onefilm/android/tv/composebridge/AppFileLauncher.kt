package com.onefilm.android.tv.composebridge

import android.content.Context
import android.content.Intent
import com.onefilm.android.tv.compose.bridge.FileLauncher
import com.onefilm.android.tv.ui.activity.FileActivity

class AppFileLauncher : FileLauncher {
    override fun open(context: Context) {
        context.startActivity(Intent(context, FileActivity::class.java))
    }
}
