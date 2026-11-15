package com.onefilm.android.tv.compose.bridge

/** 打开一个网页（配置中心这类站点给的 URL），App 层用网页登录页承载。 */
interface WebPageLauncher {
    suspend fun open(url: String): Boolean
}
