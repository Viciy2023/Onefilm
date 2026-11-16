package com.onefilm.android.tv.compose.bridge

/** 扫码登录二维码：内容类（走 QrCodes 渲染）或图片地址类（直接加载）。 */
data class PanQr(
    val title: String,
    val message: String,
    val provider: String,
    val text: String? = null,
    val imageUrl: String? = null,
)

sealed interface PanQrState {
    data object Waiting : PanQrState
    data object Scanned : PanQrState
    data object Success : PanQrState
    data object Expired : PanQrState
    data class Error(val message: String) : PanQrState
}

/**
 * 配置中心（spider 自带网盘面板）的动作接口：
 * 生成二维码、轮询扫码状态、清除 Cookie 等。
 */
interface PanConfigActions {
    /** @param provider quark / uc / baidu / thunder / guangya / pan123 / pan115 / pan189 / pan139 / bili */
    suspend fun startLogin(siteKey: String, provider: String, title: String): PanQr?

    /** 轮询扫码状态；成功后实现方应把结果写回 spider（由 spider 接口完成）。 */
    suspend fun pollLogin(siteKey: String, provider: String): PanQrState

    /** 无页面的动作：clearQuarkCookie / setPanOrder 等，action 为完整 query 片段。 */
    suspend fun runAction(siteKey: String, action: String): Boolean
}
