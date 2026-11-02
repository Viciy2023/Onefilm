package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.App
import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.HomeDataSource
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeFeed
import com.onefilm.android.tv.compose.bridge.HomeSection
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppHomeDataSource : HomeDataSource {

    /**
     * 磁盘缓存按接口地址隔离。
     *
     * <p>之前用固定的 "home" 作为 key，换接口后冷启动会先渲染上一份接口的首页，
     * 包括它的分类列表；用户点到那些分类时，新的接口上自然找不到对应分类，
     * 就会报「分类加载失败」。
     */
    private fun cacheKey(): String = "home_" + VodConfig.getUrl().orEmpty().hashCode()

    init {
        HomeFeedCache.feed = runCatching {
            DiskCache.read<HomeFeed>(cacheKey(), 24 * 60 * 60_000L)
        }.getOrNull()
    }

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
        ).also {
            HomeFeedCache.feed = it
            DiskCache.write(cacheKey(), it)
        }
    }
}
