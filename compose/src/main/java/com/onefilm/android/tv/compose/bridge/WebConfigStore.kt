package com.onefilm.android.tv.compose.bridge

/**
 * 配置中心这类站点的网页入口。
 *
 * 接口会把「网页版配置中心」的地址放在某条卡片的备注里（例如
 * `http://192.168.1.4:19989/config`）。这里按站点记住这个基址，
 * 其余动作卡片用 `基址#分类id` 打开同一页面的对应分区。
 */
object WebConfigStore {

    private val bases = mutableMapOf<String, String>()

    fun put(siteKey: String, url: String) {
        if (siteKey.isBlank() || url.isBlank()) return
        val base = url.substringBefore('#')
        if (!base.startsWith("http://") && !base.startsWith("https://")) return
        bases[siteKey] = base
    }

    fun get(siteKey: String): String? = bases[siteKey]

    /** 组合某个分类对应的页面地址；没有基址时返回 null。 */
    fun pageUrl(siteKey: String, section: String?): String? {
        val base = get(siteKey) ?: return null
        return if (section.isNullOrBlank()) base else "$base#$section"
    }
}
