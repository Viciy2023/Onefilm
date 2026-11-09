package com.onefilm.android.tv.compose.bridge

import android.content.Context
import android.content.Intent

/** 打开旧版文件/网盘入口。 */
interface FileLauncher {
    fun open(context: Context)
    fun intent(context: Context): Intent? = null
}
