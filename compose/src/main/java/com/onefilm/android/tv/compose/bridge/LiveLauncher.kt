package com.onefilm.android.tv.compose.bridge

import android.content.Context

/** 直接打开旧版直播入口，复用其配置、频道与播放逻辑。 */
interface LiveLauncher {
    fun open(context: Context)
}
