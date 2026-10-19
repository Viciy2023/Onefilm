package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class AppSearchDataSource : SearchDataSource {

    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        if (keyword.isBlank()) return@withContext SearchPage(emptyList())

        val sites = VodConfig.get().getSites().filter { it.isSearchable }.take(8)
        val results = coroutineScope {
            sites.map { site ->
                async {
                    runCatching {
                        withTimeout(6_000) {
                            SiteApi.searchContent(site, keyword, false, page.toString()).list
                        }
                    }.onSuccess {
                        android.util.Log.i("OneFilmSearch", "${site.name} -> ${it.size}")
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
        SearchPage(items)
    }
}
