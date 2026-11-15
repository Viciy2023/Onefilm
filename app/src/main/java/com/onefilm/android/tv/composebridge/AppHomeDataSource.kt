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
import com.onefilm.android.tv.compose.bridge.HomeSiteOption
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.feature.category.CategoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppHomeDataSource : HomeDataSource {

    /**
     * 磁盘缓存按「接口地址 + 首页站点」隔离。
     *
     * <p>之前用固定的 "home" 作为 key，换接口后冷启动会先渲染上一份接口的首页；
     * 现在首页站点可切换，缓存不区分站点同样会先闪上一个站点的内容。
     */
    private fun cacheKey(): String {
        val configUrl = VodConfig.getUrl().orEmpty()
        val siteKey = runCatching { VodConfig.get().getHome().key }.getOrNull().orEmpty()
        return "home_" + configUrl.hashCode() + "_" + siteKey.hashCode() + "_" +
            com.github.catvod.utils.LocaleManager.get()
    }

    init {
        HomeFeedCache.feed = runCatching {
            DiskCache.read<HomeFeed>(cacheKey(), 24 * 60 * 60_000L)
        }.getOrNull()
    }

    override fun sites(): List<HomeSiteOption> {
        val homeKey = runCatching { VodConfig.get().getHome().key }.getOrNull().orEmpty()
        return VodConfig.get().getSites()
            .filter { it.key.isNotBlank() && !it.isHide() }
            .map { HomeSiteOption(it.key, it.name.orEmpty(), it.key == homeKey) }
    }

    override fun setHomeSite(key: String): Boolean {
        val site = VodConfig.get().getSite(key)
        if (site.key.isBlank()) return false
        // 持久化 + RefreshEvent.home() 都在 VodConfig 里完成
        VodConfig.get().setHome(site)
        // 站点变了：内存首页与分类缓存都属于上一个站点，必须失效
        HomeFeedCache.feed = null
        CategoryCache.clear()
        return true
    }

    override suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {        VodConfig.get().ensureLoaded()
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
                add(HomeSection("", it, key = "history"))
            }
            HomeFeedMapper.fromKeeps(Keep.getVod()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("", it, key = "favorites"))
            }
            if (vods.isNotEmpty()) add(HomeSection("", vods, key = "recommend"))
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
