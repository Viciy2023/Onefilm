package com.onefilm.android.tv.compose.bridge

/** 打开一个网页（配置中心这类站点给的 URL），App 层用网页登录页承载。 */
interface WebPageLauncher {
    /**
     * @param selectCard 非空时，页面加载后调用站点的 selectCard(name)，直接展开该卡片的二维码/输入框
     */
    suspend fun open(url: String, selectCard: String? = null): Boolean
}
