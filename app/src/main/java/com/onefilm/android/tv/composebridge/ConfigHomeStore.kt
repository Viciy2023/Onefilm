package com.onefilm.android.tv.composebridge

import android.content.Context
import com.onefilm.android.tv.App

/**
 * 记录「接口 JSON 自己声明的主页源」。
 *
 * 每个接口 JSON 都有 `"home"`（或默认第一个站点）作为首页数据源；
 * 用户在客户端切换「站点」只影响分类/搜索，首页影片资源始终用这里的源，
 * 这样不管切到哪个站点，首页都有影视数据。
 *
 * 注意：VodConfig.setHome(site) 会把选中的站点写回 config.home，
 * 所以必须在加载配置时就把 JSON 里的值记下来。
 */
object ConfigHomeStore {

    private const val PREFS = "onefilm_config_home"

    private fun prefs() = App.get().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 首次加载某接口时记录它的主页源；之后不再覆盖。 */
    fun capture(configUrl: String, homeKey: String) {
        if (configUrl.isBlank() || homeKey.isBlank()) return
        val sp = prefs()
        if (!sp.getString(configUrl, null).isNullOrBlank()) return
        sp.edit().putString(configUrl, homeKey).apply()
    }

    fun get(configUrl: String): String? {
        if (configUrl.isBlank()) return null
        return prefs().getString(configUrl, null)?.takeIf { it.isNotBlank() }
    }
}
