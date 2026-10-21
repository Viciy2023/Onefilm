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
        android.util.Log.d("ComposeHome", "site=${site.key} list=${result.list.size} firstId=${result.list.firstOrNull()?.id} firstName=${result.list.firstOrNull()?.name} firstPic=${result.list.firstOrNull()?.pic}")
        val vods = HomeFeedMapper.fromVods(
            result.list.filter { it.name.orEmpty().isNotBlank() },
            site.key,
        )
        val hero = (vods.filter { !it.posterUrl.isNullOrBlank() }.ifEmpty { vods }).take(5)
        val categories = result.types.map { HomeCategory(site.key, it.typeId, it.typeName) }
        CategoryFilterStore.put(result.types)
        val sections = buildList {
            HomeFeedMapper.fromHistory(History.get()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("继续观看", it))
            }
            HomeFeedMapper.fromKeeps(Keep.getVod()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("收藏", it))
            }
            if (vods.isNotEmpty()) add(HomeSection("推荐", vods))
        }
        HomeFeed(
            hero = hero,
            sections = sections,
            categories = categories,
            siteName = site.name.orEmpty(),
            notice = result.msg.orEmpty(),
        )
    }
}
