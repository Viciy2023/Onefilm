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
        // 推荐源也要重新挑（不同站点组合下能用的源可能不同）
        recommendKey = null
        return true
    }

    override suspend fun load(): HomeFeed = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val site = VodConfig.get().getHome()
        if (site.key.isNullOrBlank()) {
            return@withContext HomeFeed(emptyList(), emptyList())
        }

        val primary = runCatching { SiteApi.homeContent(site) }.getOrNull()
        var typeSource: Result? = primary
        var list = primary?.list.orEmpty().filter { !it.name.orEmpty().isNullOrBlank() }
        var vodsSite = site

        // 所选站点没有首页推荐时（大多数采集站都是这样），自动借用「推荐源」的影视数据，
        // 保证像欧歌一样：无论切到哪个站点，首页都有内容。
        if (!hasMoviePoster(list)) {
            val fallback = pickRecommendSite(site.key)
            if (fallback != null) {
                val fallbackResult = runCatching { SiteApi.homeContent(fallback) }.getOrNull()
                val fallbackList = fallbackResult?.list.orEmpty()
                    .filter { !it.name.orEmpty().isNullOrBlank() }
                if (fallbackList.isNotEmpty()) {
                    list = fallbackList
                    vodsSite = fallback
                    if (typeSource?.types.isNullOrEmpty()) typeSource = fallbackResult
                }
            }
        }

        val types = typeSource?.types.orEmpty()
        android.util.Log.d(
            "ComposeHome",
            "home=${site.key} vodsFrom=${vodsSite.key} list=${list.size} types=${types.size}",
        )

        val vods = HomeFeedMapper.fromVods(list, vodsSite.key)
        val hero = (vods.filter { !it.posterUrl.isNullOrBlank() }.ifEmpty { vods }).take(5)
        val categories = types.map { HomeCategory(site.key, it.typeId, it.typeName) }
        CategoryFilterStore.put(types)
        val recommendTitle = if (vodsSite.key != site.key) {
            "推荐 · " + vodsSite.name.orEmpty()
        } else {
            ""
        }
        val sections = buildList {
            HomeFeedMapper.fromHistory(History.get()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("", it, key = "history"))
            }
            HomeFeedMapper.fromKeeps(Keep.getVod()).takeIf { it.isNotEmpty() }?.let {
                add(HomeSection("", it, key = "favorites"))
            }
            if (vods.isNotEmpty()) add(HomeSection(recommendTitle, vods, key = "recommend"))
        }
        HomeFeed(
            hero = hero,
            sections = sections,
            categories = categories,
            siteName = site.name.orEmpty(),
            notice = primary?.msg.orEmpty(),
            // 站点自己的首页没有影片（只有动作卡/分类）→ 纯设置型站点
            settingsStyleSite = !hasMoviePoster(
                primary?.list.orEmpty().filter { !it.name.orEmpty().isNullOrBlank() },
            ) && categories.isNotEmpty(),
        ).also {
            HomeFeedCache.feed = it
            DiskCache.write(cacheKey(), it)
        }
    }

    /** 首页条目里有多少带海报的影片；配置中心那种动作卡没有海报，会被排除。 */
    private fun hasMoviePoster(list: List<com.onefilm.android.tv.bean.Vod>): Boolean =
        list.count { !it.pic.orEmpty().isNullOrBlank() } >= 3

    /**
     * 挑一个「首页推荐源」：优先上次成功过的站点，其次按配置顺序试几个，
     * 谁能在首页返回带海报的影片就用谁。
     */
    private fun pickRecommendSite(excludeKey: String): Site? {
        val sites = VodConfig.get().getSites()
            .filter { it.key.isNotBlank() && !it.isHide() && it.key != excludeKey }
        if (sites.isEmpty()) return null
        recommendKey?.let { key ->
            sites.firstOrNull { it.key == key }?.let { remembered ->
                val ok = runCatching { SiteApi.homeContent(remembered) }.getOrNull()
                    ?.let { hasMoviePoster(it.list.orEmpty()) } ?: false
                if (ok) return remembered
            }
        }
        var tried = 0
        for (candidate in sites) {
            if (tried >= MAX_PROBE) break
            tried++
            val result = runCatching { SiteApi.homeContent(candidate) }.getOrNull() ?: continue
            if (hasMoviePoster(result.list.orEmpty())) {
                recommendKey = candidate.key
                return candidate
            }
        }
        return null
    }

    companion object {
        /** 探测推荐源时最多试几个站点，避免首页加载过慢。 */
        private const val MAX_PROBE = 4

        @Volatile
        private var recommendKey: String? = null
    }
}
