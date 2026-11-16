package com.onefilm.android.tv.compose.bridge

/** 表单弹窗里的一个输入项。 */
data class PanField(
    val key: String,
    val label: String,
    val value: String = "",
    val multiline: Boolean = false,
)

/** 表单弹窗里的附加按钮，例如「获取验证码」；query 里的 {key} 会替换成输入值。 */
data class PanFormExtra(
    val label: String,
    val query: String,
)

/** 表单弹窗定义：保存 / 附加动作 / 清除都用同一种 query 模板。 */
data class PanFormSpec(
    val title: String,
    val subtitle: String,
    val fields: List<PanField>,
    val extras: List<PanFormExtra> = emptyList(),
    val saveLabel: String = "保存",
    val saveQuery: String? = null,
    val clearLabel: String? = null,
    val clearQuery: String? = null,
)

/** Emby 服务器条目。 */
data class PanEmbyServer(
    val name: String,
    val url: String,
    val user: String,
)

/**
 * 配置中心里需要「填表单」的卡片 → 弹窗定义。
 *
 * 接口页面的实现见 spider 的配置页：手机号验证码登录、账号密码登录、
 * DIY 远程配置地址、WebDAV 备份。TV 与手机共用。
 */
object PanForms {

    fun resolve(categoryId: String?, title: String): PanFormSpec? = when {
        title.contains("手机号") || title.contains("短信") -> smsForm(categoryId, title)
        title.contains("账号密码") -> accountForm(categoryId, title)
        title.contains("配置地址") -> urlForm(title)
        title.contains("备份") -> backupForm(title)
        else -> null
    }

    private fun smsForm(categoryId: String?, title: String): PanFormSpec? {
        val id = categoryId?.trim()?.lowercase().orEmpty()
        val thunder = id == "thunder" || title.contains("迅雷")
        val yd = id == "pan139" || title.contains("移动")
        return when {
            thunder -> PanFormSpec(
                title = title,
                subtitle = "迅雷使用手机号 + 短信验证码登录，登录成功后播放时会自动刷新 token。",
                fields = listOf(
                    PanField("mobile", "手机号"),
                    PanField("code", "短信验证码"),
                ),
                extras = listOf(PanFormExtra("获取验证码", "action=sendThunderSms&mobile={mobile}")),
                saveLabel = "登录保存",
                saveQuery = "action=loginThunderSms&code={code}",
            )
            yd -> PanFormSpec(
                title = title,
                subtitle = "移动云盘使用手机号 + 短信验证码登录。",
                fields = listOf(
                    PanField("phone", "手机号"),
                    PanField("code", "短信验证码"),
                ),
                extras = listOf(PanFormExtra("获取验证码", "action=sendPan139Sms&phone={phone}")),
                saveLabel = "登录保存",
                saveQuery = "action=loginPan139Sms&phone={phone}&code={code}",
            )
            else -> null
        }
    }

    private fun accountForm(categoryId: String?, title: String): PanFormSpec? {
        val id = categoryId?.trim()?.lowercase().orEmpty()
        return when {
            id == "pan123" || title.contains("123") -> PanFormSpec(
                title = title,
                subtitle = "123 使用账号 + 密码登录；如果网页端提示验证码，请先在 123 网页处理。",
                fields = listOf(
                    PanField("account", "123 账号"),
                    PanField("password", "123 密码"),
                ),
                saveLabel = "登录保存",
                saveQuery = "action=setPan123Account&account={account}&password={password}",
                clearLabel = "清除账号",
                clearQuery = "action=clearPan123Account",
            )
            id == "pan189" || title.contains("天翼") -> PanFormSpec(
                title = title,
                subtitle = "天翼网盘使用账号 + 密码 + 验证码登录，验证码可留空。",
                fields = listOf(
                    PanField("account", "天翼账号"),
                    PanField("password", "天翼密码"),
                    PanField("code", "验证码（可空）"),
                ),
                saveLabel = "登录保存",
                saveQuery = "action=loginPan189Account&account={account}&password={password}&code={code}",
                clearLabel = "清除天翼 Cookie",
                clearQuery = "action=clearPan189Cookie",
            )
            else -> null
        }
    }

    private fun urlForm(title: String): PanFormSpec? = when {
        title.contains("AList", ignoreCase = true) -> PanFormSpec(
            title = title,
            subtitle = "保存 AList 远程配置地址，例如 https://9280.kstore.vip/alist.json。",
            fields = listOf(PanField("value", "远程配置地址")),
            saveQuery = "action=setAListUrl&value={value}",
            clearLabel = "清除地址",
            clearQuery = "action=clearAListUrl",
        )
        title.contains("WebDav", ignoreCase = true) -> PanFormSpec(
            title = title,
            subtitle = "保存 WebDav 远程配置地址，例如 https://9280.kstore.vip/webdav.json。",
            fields = listOf(PanField("value", "远程配置地址")),
            saveQuery = "action=setWebDavUrl&value={value}",
            clearLabel = "清除地址",
            clearQuery = "action=clearWebDavUrl",
        )
        title.contains("Vod", ignoreCase = true) -> PanFormSpec(
            title = title,
            subtitle = "保存 DiyVod 远程配置地址，例如 https://9280.kstore.vip/diyvod.txt。",
            fields = listOf(PanField("value", "远程配置地址")),
            saveQuery = "action=setDiyVodUrl&value={value}",
            clearLabel = "清除地址",
            clearQuery = "action=clearDiyVodUrl",
        )
        else -> null
    }

    private fun backupForm(title: String): PanFormSpec = PanFormSpec(
        title = title,
        subtitle = "WebDAV 备份需要用地址 + 用户名 + 密码；本地备份保存到 App 私有目录。",
        fields = listOf(
            PanField("server", "WebDAV 地址", multiline = false),
            PanField("user", "WebDAV 用户名"),
            PanField("pass", "WebDAV 密码"),
        ),
        extras = listOf(
            PanFormExtra("本地备份", "action=runLocalBackup"),
            PanFormExtra("WebDAV 备份", "action=runWebDavBackup&server={server}&user={user}&pass={pass}"),
            PanFormExtra("WebDAV 还原", "action=runWebDavRecover&server={server}&user={user}&pass={pass}"),
        ),
        saveLabel = "保存 WebDAV",
        saveQuery = "action=saveBackupConfig&server={server}&user={user}&pass={pass}",
        clearLabel = "清除 WebDAV",
        clearQuery = "action=clearBackupConfig",
    )

    /** 用输入值替换 query 模板里的 {key}。 */
    fun buildQuery(template: String, values: Map<String, String>): String {
        var query = template
        values.forEach { (key, value) ->
            query = query.replace("{$key}", android.net.Uri.encode(value.trim()))
        }
        return query
    }
}
