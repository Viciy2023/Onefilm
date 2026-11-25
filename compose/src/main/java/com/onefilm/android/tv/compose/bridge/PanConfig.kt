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

    /** 同上，但把接口的 code / msg 一起返回，供表单弹窗做反馈。 */
    suspend fun runCommand(siteKey: String, action: String): PanCommandResult

    /**
     * 读取配置状态（接口 `action=status`）。值统一转成字符串：数组用逗号拼接，
     * 布尔用 true/false，取不到时返回空 Map。
     */
    suspend fun status(siteKey: String): Map<String, String>

    /** 日志列表（接口 `action=logList`）。 */
    suspend fun logs(siteKey: String): List<PanLogEntry>

    /** Emby 服务器列表（接口 `action=status` 的 `embyServers`）。 */
    suspend fun embyServers(siteKey: String): List<PanEmbyServer>
}

/** 从状态里取单个字段（注册链接、当前排序值等）。 */
suspend fun PanConfigActions.statusValue(siteKey: String, field: String): String? =
    status(siteKey)[field]

/** 动作执行结果：ok 由接口的 code==0 决定，message 是接口自己的提示。 */
data class PanCommandResult(
    val ok: Boolean,
    val message: String,
)

/** 配置中心里需要二次确认的动作（例如清空 Cookie）。 */
data class PanConfirmState(
    val siteKey: String,
    val title: String,
    val message: String,
    val action: String,
    /** 弹窗正文（可空）；清除类动作才需要额外说明。 */
    val body: String? = null,
)
