package com.onefilm.android.tv.compose.bridge

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 将 SEND/VIEW 外部 Intent 交给 app 层分类后写入 [ExternalPlaybackRequest]。 */
interface ExternalIntentHandler {
    fun handle(context: Context, intent: Intent)
}

/**
 * 外部链接播放请求：App 层写入，Compose 宿主消费后交给新播放器，
 * 不再启动旧 VideoActivity。
 *
 * @param direct true 表示本地 content/file URI，直接交给 Media3，跳过 push_agent 解析。
 */
object ExternalPlaybackRequest {
    data class Request(val url: String, val direct: Boolean = false)

    var pending by mutableStateOf<Request?>(null)
}
