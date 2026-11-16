package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.Web
import com.onefilm.android.tv.compose.bridge.WebPageLauncher

/** 用网页登录页承载接口下发的 URL（配置中心这类站点的动作项）。 */
class AppWebPageLauncher : WebPageLauncher {

    override suspend fun open(url: String, selectCard: String?): Boolean {
        if (url.isBlank()) return false
        val escaped = url.replace("\\", "\\\\").replace("\"", "\\\"")
        val web = Web.objectFrom("""{"url":"$escaped","mode":"view"}""") ?: return false
        return AppWebLogin.login(web, selectCard)
    }
}
