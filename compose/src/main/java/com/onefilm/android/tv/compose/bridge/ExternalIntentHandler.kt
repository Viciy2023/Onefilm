package com.onefilm.android.tv.compose.bridge

import android.content.Context
import android.content.Intent

/** 将 SEND/VIEW 外部 Intent 交给 app 层承载旧播放器入口。 */
interface ExternalIntentHandler {
    fun handle(context: Context, intent: Intent)
}
