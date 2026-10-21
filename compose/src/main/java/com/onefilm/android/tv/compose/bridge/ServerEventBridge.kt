package com.onefilm.android.tv.compose.bridge

import kotlinx.coroutines.flow.MutableSharedFlow

data class PushedSetting(
    val text: String,
    val name: String,
)

/** 旧版网页推送事件到 Compose 设置弹窗的桥接。 */
object ServerEventBridge {
    private val _settings = MutableSharedFlow<PushedSetting>(extraBufferCapacity = 4)
    val settings = _settings

    fun pushSetting(text: String, name: String) {
        _settings.tryEmit(PushedSetting(text, name))
    }
}
