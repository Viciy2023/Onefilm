package com.onefilm.android.tv.compose.bridge

/** 配置中心「综合设置」里的单条日志。 */
data class PanLogEntry(
    val name: String,
    val size: String,
    val time: String,
)

/**
 * 「综合设置」里的设置项 → 原生弹窗的映射。
 *
 * 每一种都对应接口页面里的一张卡片；没有映射的（备份、蜗牛影视、Go 状态等）
 * 仍然打开接口页面本身。TV 与手机共用这一份映射。
 */
sealed interface PanSetting {

    /** 单选型设置：保存时调用 `action=<setter>&value=<选项值>`。 */
    data class Choice(
        val title: String,
        val subtitle: String,
        val field: String,
        val setter: String,
        val options: List<Pair<String, String>>,
    ) : PanSetting

    /** 网盘线路排序。 */
    data object Order : PanSetting

    /** 日志列表。 */
    data object Logs : PanSetting

    /** Go 多线程运行状态（只读）。 */
    data object GoStatus : PanSetting
}

object PanSettings {

    private val dnsOptions = listOf(
        "223.5.5.5" to "阿里 DNS · 223.5.5.5",
        "119.29.29.29" to "腾讯 DNS · 119.29.29.29",
        "114.114.114.114" to "公共 DNS · 114.114.114.114",
        "180.76.76.76" to "百度 DNS · 180.76.76.76",
    )

    /** 线路排序里显示的网盘名称。 */
    val orderLabels = mapOf(
        "quark" to "夸克",
        "uc" to "UC",
        "baidu" to "百度",
        "pan115" to "115",
        "pan123" to "123",
        "pan189" to "天翼",
        "pan139" to "移动",
        "guangya" to "光鸭",
        "thunder" to "迅雷",
        "direct" to "直连",
    )

    fun resolve(title: String): PanSetting? = when {
        title.contains("排序") -> PanSetting.Order
        title.contains("日志") -> PanSetting.Logs
        // Go 多线程 DNS 是单选题，必须先于「Go + 多线程」的状态页判断
        title.contains("DNS") -> PanSetting.Choice(
            title = title,
            subtitle = "保存后重启 App 生效。",
            field = "goDns",
            setter = "setGoDns",
            options = dnsOptions,
        )
        title.contains("Go") && title.contains("多线程") -> PanSetting.GoStatus
        title.contains("播放选项") || title.contains("极速") -> PanSetting.Choice(
            title = title,
            subtitle = "关闭后选集里不再显示极速线路。",
            field = "showTranscode",
            setter = "setTranscode",
            options = listOf("true" to "显示极速线路", "false" to "隐藏极速线路"),
        )
        title.contains("多线程") -> PanSetting.Choice(
            title = title,
            subtitle = "Java 模式用内置分片代理，Go 模式走外部 Go 服务。",
            field = "threadProxyMode",
            setter = "setThreadProxyMode",
            options = listOf("java" to "Java 多线程", "go" to "Go 多线程"),
        )
        title.contains("弹幕") -> PanSetting.Choice(
            title = title,
            subtitle = "控制选集播放时是否自动获取弹幕以及弹幕颜色。",
            field = "danmuMode",
            setter = "setDanmuMode",
            options = listOf(
                "1" to "自动弹幕 · 纯白",
                "2" to "自动弹幕 · 彩色",
                "3" to "关闭自动弹幕",
            ),
        )
        title.contains("鸿蒙") -> PanSetting.Choice(
            title = title,
            subtitle = "开启后 Go 多线程按鸿蒙兼容模式使用 SO，保存后重启 App 生效。",
            field = "hongMengMode",
            setter = "setHongMeng",
            options = listOf("true" to "开启鸿蒙兼容", "false" to "关闭鸿蒙兼容"),
        )
        // 搜索源开关：网盘搜索并发调用的四个源，关掉就不再请求
        title.contains("搜索源") && title.contains("CloudLink", ignoreCase = true) -> PanSetting.Choice(
            title = title,
            subtitle = "自建 CloudLink Finder 的搜索接口（自带链接有效性校验）。",
            field = "srcCloudlink",
            setter = "setSrcCloudlink",
            options = listOf("true" to "开启", "false" to "关闭"),
        )
        title.contains("搜索源") && title.contains("pansou", ignoreCase = true) -> PanSetting.Choice(
            title = title,
            subtitle = "pansou.app 的公开实例，覆盖夸克/百度/阿里/迅雷/UC。",
            field = "srcPansou",
            setter = "setSrcPansou",
            options = listOf("true" to "开启", "false" to "关闭"),
        )
        title.contains("搜索源") && title.contains("gesoso", ignoreCase = true) -> PanSetting.Choice(
            title = title,
            subtitle = "gesoso.fun（原 MiSou），只覆盖夸克/UC。",
            field = "srcGesoso",
            setter = "setSrcGesoso",
            options = listOf("true" to "开启", "false" to "关闭"),
        )
        title.contains("搜索源") && title.contains("pansearch", ignoreCase = true) -> PanSetting.Choice(
            title = title,
            subtitle = "pansearch.me，当前网络下不可达，留作备用。",
            field = "srcPansearch",
            setter = "setSrcPansearch",
            options = listOf("true" to "开启", "false" to "关闭"),
        )
        else -> null
    }
}
