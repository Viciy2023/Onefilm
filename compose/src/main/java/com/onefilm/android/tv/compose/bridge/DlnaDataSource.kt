package com.onefilm.android.tv.compose.bridge

data class DlnaDevice(val id: String, val name: String)

/** 手机端 DLNA 投屏桥接（仅 mobile flavor 注册）。 */
interface DlnaDataSource {
    suspend fun devices(): List<DlnaDevice>

    fun cast(
        deviceId: String,
        title: String,
        url: String,
        positionMs: Long,
        headers: Map<String, String>,
    )
}
