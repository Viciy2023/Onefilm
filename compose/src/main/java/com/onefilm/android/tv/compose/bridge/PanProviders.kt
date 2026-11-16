package com.onefilm.android.tv.compose.bridge

/**
 * 配置中心分类 → 接口参数的映射。
 *
 * 分类 id 来自接口页面自己的导航：settings / quark / uc / baidu / thunder / guangya /
 * pan123 / pan115 / pan189 / pan139 / bili / diy。
 * 扫码接口的 provider 与分类 id 不完全相同（UC 分类对应 ucCookie / ucToken），
 * 这里统一转换，TV 与手机共用同一份逻辑。
 */
object PanProviders {

    /** 扫码登录用的 provider；不支持扫码时返回 null。 */
    fun login(categoryId: String?, title: String): String? {
        val id = categoryId?.trim()?.lowercase().orEmpty()
        val byId = when (id) {
            "quark" -> "quark"
            "uc" -> if (title.contains("TV", ignoreCase = true)) "ucToken" else "ucCookie"
            "baidu" -> "baidu"
            "pan115" -> "pan115"
            "pan189" -> "pan189"
            "bili" -> "bili"
            "guangya" -> "guangya"
            "pan1me" -> "pan1me"
            else -> null
        }
        if (byId != null) return byId
        // 分类 id 不是已知网盘（例如站点改了 id）时按标题兜底
        val tv = title.contains("TV", ignoreCase = true)
        return when {
            title.contains("夸克") -> "quark"
            title.contains("UC", ignoreCase = true) -> if (tv) "ucToken" else "ucCookie"
            title.contains("百度") -> "baidu"
            title.contains("115") -> "pan115"
            title.contains("天翼") || title.contains("189") -> "pan189"
            title.contains("哔哩") || title.contains("B站", ignoreCase = true) -> "bili"
            title.contains("光鸭") -> "guangya"
            title.contains("123") -> "pan1me"
            else -> null
        }
    }

    /** 手动粘贴 Cookie / Token / 远程地址用的 provider，与扫码 provider 不一定相同。 */
    fun manual(categoryId: String?, title: String): String? {
        val id = categoryId?.trim()?.lowercase().orEmpty()
        if (id == "diy") {
            return when {
                title.contains("AList", ignoreCase = true) -> "alistUrl"
                title.contains("WebDav", ignoreCase = true) -> "webDavUrl"
                title.contains("Vod", ignoreCase = true) -> "diyVodUrl"
                else -> null
            }
        }
        if (id == "pan1me" || title.contains("分享社区")) return "pan1me"
        if (title.contains("蜗牛")) return "woniu"
        // 这些平台没有扫码，但有账号/验证码登录与清除动作
        if (id == "pan123" || id == "pan139" || id == "thunder") return id
        return login(categoryId, title)
    }

    /** 注册二维码链接在 `action=status` 里的字段名。 */
    fun registerField(categoryId: String?, title: String): String? {
        val id = categoryId?.trim()?.lowercase().orEmpty()
        return when {
            id == "quark" || title.contains("夸克") -> "qkRegister"
            id == "uc" || title.contains("UC", ignoreCase = true) -> "ucRegister"
            id == "baidu" || title.contains("百度") -> "baiduRegister"
            id == "thunder" || title.contains("迅雷") -> "xlRegister"
            id == "guangya" || title.contains("光鸭") -> "gyRegister"
            id == "pan139" || title.contains("移动") -> "ydRegister"
            else -> null
        }
    }

    /** 手动粘贴 Cookie 的接口 action 名；参数名统一为 `value`。 */
    fun cookieSetter(provider: String?): String? = when (provider) {
        "quark" -> "setQuarkCookie"
        "ucCookie" -> "setUcCookie"
        "ucToken" -> "setUcToken"
        "baidu" -> "setBaiduCookie"
        "pan115" -> "setPan115Cookie"
        "pan189" -> "setPan189Cookie"
        "bili" -> "setBiliCookie"
        "guangya" -> "setGuangYaToken"
        "guangyaZhenYing" -> "setGuangYaZhenYingCookie"
        "pan1me" -> "setPan1MeCookie"
        "woniu" -> "setWoNiu4KCookie"
        "alistUrl" -> "setAListUrl"
        "webDavUrl" -> "setWebDavUrl"
        "diyVodUrl" -> "setDiyVodUrl"
        else -> null
    }

    /** 清除 Cookie / Token 的接口 action 名。 */
    fun cookieClearer(provider: String?): String? = when (provider) {
        "quark" -> "clearQuarkCookie"
        "ucCookie", "ucToken" -> "clearUc"
        "baidu" -> "clearBaiduCookie"
        "pan115" -> "clearPan115Cookie"
        "pan189" -> "clearPan189Cookie"
        "bili" -> "clearBiliCookie"
        "guangya" -> "clearGuangYaToken"
        "guangyaZhenYing" -> "clearGuangYaZhenYingCookie"
        "pan1me" -> "clearPan1MeCookie"
        "woniu" -> "clearWoNiu4K"
        "alistUrl" -> "clearAListUrl"
        "webDavUrl" -> "clearWebDavUrl"
        "diyVodUrl" -> "clearDiyVodUrl"
        "pan123" -> "clearPan123Account"
        "pan139" -> "clearPan139"
        "thunder" -> "clearThunder"
        else -> null
    }

    /** 粘贴 Cookie 输入框的提示语，用 provider 区分 Cookie / Token。 */
    fun cookieHint(provider: String?): String = when (provider) {
        "ucToken" -> "请粘贴 UC TV Token"
        "guangya" -> "请粘贴光鸭 Token"
        "alistUrl" -> "请填写 AList 远程配置地址"
        "webDavUrl" -> "请填写 WebDav 远程配置地址"
        "diyVodUrl" -> "请填写 Vod 远程配置地址"
        else -> "请粘贴 Cookie"
    }

    /** 「手动粘贴 Cookie / 手动配置」类卡片的标题特征。 */
    fun isManualTitle(title: String): Boolean =
        title.contains("手动输入") ||
            title.contains("手动配置") ||
            title.contains("粘贴") ||
            title.contains("Cookie") ||
            (title.contains("手动") && title.contains("Cookie"))

    /** 清除类卡片对应的接口 action；没有对应动作时返回 null。 */
    fun clearAction(categoryId: String?, title: String): String? =
        cookieClearer(manual(categoryId, title))

    /**
     * 标题是不是「配置动作卡」（注册/扫码/Cookie/设置项…）。
     *
     * 配置中心这类站点的分类页里同时存在动作卡和真实影片卡：动作卡走弹窗，
     * 影片卡必须走详情/搜索，不能因为「这个站点是配置站」就全部打开接口页面。
     */
    fun isActionTitle(title: String): Boolean {
        if (title.isBlank()) return false
        val keys = listOf(
            "注册", "扫码", "手动输入", "手动配置", "粘贴", "清除", "排序", "日志",
            "弹幕", "鸿蒙", "播放选项", "多线程", "DNS", "备份", "网页版配置中心",
            "手机号", "短信", "账号密码", "刷新登录", "重建设备", "配置地址",
            "Emby", "Cookie", "Token", "蜗牛",
        )
        return keys.any { title.contains(it, ignoreCase = true) }
    }
}
