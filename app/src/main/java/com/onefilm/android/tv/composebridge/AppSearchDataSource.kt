package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchPage
import com.github.catvod.utils.Trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

class AppSearchDataSource : SearchDataSource {

    private val cache = ConcurrentHashMap<String, List<MediaCard>>()

    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val key = Trans.t2s(keyword).trim()
        if (key.isEmpty()) return@withContext SearchPage(emptyList())
        if (page == 1) cache[key]?.let { return@withContext SearchPage(it) }

        // 与旧版 VodBrowse 一致：全部可搜索站点并行，单站 5 秒超时，最多 50 条。
        val sites = VodConfig.get().getSites().filter { it.isSearchable }
        val results = coroutineScope {
            sites.map { site ->
                async {
                    runCatching {
                        withTimeout(5_000) {
                            SiteApi.searchContent(site, key, false, page.toString()).list
                        }
                    }.onFailure {
                        android.util.Log.w("OneFilmSearch", "${site.name} failed: $it")
                    }.getOrNull().orEmpty()
                }
            }.awaitAll()
        }

        val seen = HashSet<String>()
        val items = ArrayList<MediaCard>()
        results.forEachIndexed { index, vods ->
            val siteKey = sites.getOrNull(index)?.key.orEmpty()
            HomeFeedMapper.fromVods(vods, siteKey).forEach { card ->
                if (seen.add("${card.key}:${card.id}")) items.add(card)
            }
        }

        val sorted = items.sortedByDescending { card ->
            val name = Trans.t2s(card.title)
            when {
                name == key -> 2
                name.contains(key) -> 1
                else -> 0
            }
        }.take(50)

        if (page == 1) cache[key] = sorted
        SearchPage(sorted)
    }
}
