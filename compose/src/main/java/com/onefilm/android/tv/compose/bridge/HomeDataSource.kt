package com.onefilm.android.tv.compose.bridge

import java.io.Serializable

data class MediaCard(
    val key: String,
    val id: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?,
    val progress: Float? = null,
    val siteName: String? = null,
    /** 横版海报；空时客户端用竖图 + 模糊背景兜底。 */
    val landscapeUrl: String? = null,
    /** 站点 folder=true 时，卡片表示下一级目录而不是影片。 */
    val folder: Boolean = false,
    /** 条目是「打开网页」动作时，这里带要打开的地址（来自接口返回的备注）。 */
    val webUrl: String? = null,
) : Serializable

data class HomeSection(val title: String, val items: List<MediaCard>, val key: String? = null) : Serializable

data class HomeCategory(val key: String, val id: String, val name: String) : Serializable

data class HomeFeed(
    val hero: List<MediaCard>,
    val sections: List<HomeSection>,
    val categories: List<HomeCategory> = emptyList(),
    val siteName: String = "",
    val notice: String = "",
    /** 当前站点是否「纯设置型」（首页只有动作卡/分类，没有影片推荐）。 */
    val settingsStyleSite: Boolean = false,
) : Serializable

object HomeFeedCache {
    @Volatile
    var feed: HomeFeed? = null
}

/** 首页可选站点（「主页」标签再点一次弹出的数据源面板）。 */
data class HomeSiteOption(
    val key: String,
    val name: String,
    val current: Boolean,
) : Serializable

interface HomeDataSource {
    suspend fun load(): HomeFeed

    /** 首页可选站点，已过滤隐藏站点，保持配置顺序。 */
    fun sites(): List<HomeSiteOption> = emptyList()

    /** 切换首页站点并持久化；返回是否切换成功。 */
    fun setHomeSite(key: String): Boolean = false
}
