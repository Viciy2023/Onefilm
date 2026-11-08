package com.onefilm.android.tv.compose.bridge

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 将 SEND/VIEW 外部 Intent 交给 app 层承载旧播放器入口。 */
interface ExternalIntentHandler {
    fun handle(context: Context, intent: Intent)
}

/**
 * 外部链接播放请求：App 层写入，Compose 宿主消费后交给新播放器，
 * 避免外部 Intent 再启动旧 VideoActivity。
 */
object ExternalPlaybackRequest {
    var url by mutableStateOf<String?>(null)
}
