package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.bean.Result
import com.onefilm.android.tv.bean.Site
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeDataSource
import com.onefilm.android.tv.compose.bridge.HomeFeed
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.bridge.HomeSection
import com.onefilm.android.tv.compose.bridge.HomeSiteOption
import com.onefilm.android.tv.compose.feature.category.CategoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppHomeDataSource : HomeDataSource {

    /**
     * 磁盘缓存按「接口地址 + 主页源」隔离：
     * 首页影片永远来自接口 JSON 声明的主页源，切换站点不会换内容。
     */
    private fun cacheKey(): String {
        val configUrl = VodConfig.getUrl().orEmpty()
        val homeKey = pinnedHomeKey().orEmpty()
        return "home_" + configUrl.hashCode() + "_" + homeKey.hashCode() + "_" +
            com.github.catvod.utils.LocaleManager.get()
    }

    init {
        HomeFeedCache.feed = runCatching {
            DiskCache.read<HomeFeed>(cacheKey(), 24 * 60 * 60_000L)
        }.getOrNull()
    }

    /** 接口 JSON 自己声明的主页源；取不到时退回当前站点。 */
    private fun pinnedHomeKey(): String? {
        val url = VodConfig.getUrl().orEmpty()
        val pinned = ConfigHomeStore.get(url)
        if (!pinned.isNullOrBlank() && VodConfig.get().getSite(pinned).key.isNotBlank()) return pinned
        return runCatching { VodConfig.get().getHome().key }.getOrNull()
    }

    private fun pinnedHomeSite(): Site {
        val key = pinnedHomeKey()
        val site = if (key.isNullOrBlank()) VodConfig.get().getHome() else VodConfig.get().getSite(key)
        return site.takeIf { it.key.isNotBlank() } ?: VodConfig.get().getHome()
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
        // 站点变了：分类缓存属于上一个站点，必须失效
        CategoryCache.clear()
        HomeFeedCache.feed = null
        return true
    }

    override suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val selected = VodConfig.get().getHome()
        val homeSite = pinnedHomeSite()
        if (homeSite.key.isNullOrBlank()) {
            return@withContext HomeFeed(emptyList(), emptyList())
        }

        // 首页影片：永远取接口声明的主页源
        val homeResult = runCatching { SiteApi.homeContent(homeSite) }.getOrNull()
        // 分类：跟随用户选中的站点；它没有分类时用主页源的
        val selectedResult: Result? = if (selected.key != homeSite.key) {
            runCatching { SiteApi.homeContent(selected) }.getOrNull()
        } else {
            homeResult
        }
        val typeSource = selectedResult?.takeIf { it.types.orEmpty().isNotEmpty() } ?: homeResult

        val list = homeResult?.list.orEmpty().filter { !it.name.orEmpty().isNullOrBlank() }
        val types = typeSource?.types.orEmpty()
        android.util.Log.d(
            "ComposeHome",
            "home=${homeSite.key} selected=${selected.key} list=${list.size} types=${types.size}",
        )

        val vods = HomeFeedMapper.fromVods(list, homeSite.key)
        val hero = (vods.filter { !it.posterUrl.isNullOrBlank() }.ifEmpty { vods }).take(5)
        val categories = types.map { HomeCategory(selected.key.ifBlank { homeSite.key }, it.typeId, it.typeName) }
        CategoryFilterStore.put(types)
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
            siteName = selected.name.orEmpty(),
            notice = homeResult?.msg.orEmpty(),
            // 「纯设置型」按用户当前选中的站点判断（配置中心这类站点条目全是动作卡）
            settingsStyleSite = selectedResult != null &&
                selectedResult.list.orEmpty().none { !it.pic.orEmpty().isNullOrBlank() } &&
                typeSource?.types.orEmpty().isNotEmpty(),
        ).also {
            HomeFeedCache.feed = it
            DiskCache.write(cacheKey(), it)
        }
    }
}
