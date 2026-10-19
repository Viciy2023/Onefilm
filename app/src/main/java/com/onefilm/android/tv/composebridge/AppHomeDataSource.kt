package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.HomeDataSource
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeFeed
import com.onefilm.android.tv.compose.bridge.HomeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppHomeDataSource : HomeDataSource {

    override suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val site = VodConfig.get().getHome()
        if (site.key.isNullOrBlank()) {
            return@withContext HomeFeed(emptyList(), emptyList())
        }
        val result = SiteApi.homeContent(site)
        val vods = HomeFeedMapper.fromVods(result.list, site.key)
        val categories = result.types.map { HomeCategory(site.key, it.typeId, it.typeName) }
        val sections = buildList {
            HomeFeedMapper.fromHistory(History.get()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("继续观看", it))
            }
            HomeFeedMapper.fromKeeps(Keep.getVod()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("收藏", it))
            }
            if (vods.isNotEmpty()) add(HomeSection("推荐", vods))
        }
        HomeFeed(hero = vods.take(5), sections = sections, categories = categories)
    }
}
