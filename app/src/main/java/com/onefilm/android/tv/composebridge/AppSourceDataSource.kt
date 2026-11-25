package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.SourceDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SourceItem
import com.github.catvod.utils.Trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class AppSourceDataSource : SourceDataSource {

    override suspend fun load(title: String): List<SourceItem> = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val key = Trans.t2s(title).trim()
        if (key.isEmpty()) return@withContext emptyList()

        val cached = SearchResultCache.items.filter {
            val name = Trans.t2s(it.title)
            name == key || name.contains(key)
        }
        if (cached.isNotEmpty()) {
            return@withContext cached.map { card ->
                SourceItem(
                    siteName = VodConfig.get().getSite(card.key)?.name.orEmpty(),
                    key = card.key,
                    id = card.id,
                    title = card.title,
                    subtitle = card.subtitle,
                    posterUrl = card.posterUrl,
                )
            }.distinctBy { "${it.key}:${it.id}" }
        }

        val sites = VodConfig.get().getSites().filter { it.isSearchable }
        val found = java.util.Collections.synchronizedList(mutableListOf<SourceItem>())
        coroutineScope {
            val jobs = sites.map { site ->
                async {
                    val list = runCatching {
                        withTimeout(5_000) {
                            SiteApi.searchContent(site, key, false, "1").list
                        }
                    }.getOrNull().orEmpty().filter { vod ->
                        val name = Trans.t2s(vod.name.orEmpty())
                        name == key || name.contains(key)
                    }.map { vod ->
                        SourceItem(
                            siteName = site.name.orEmpty(),
                            key = site.key,
                            id = vod.id.orEmpty(),
                            title = vod.name.orEmpty().ifBlank { key },
                            subtitle = vod.remarks.orEmpty().ifBlank { null },
                            posterUrl = vod.pic.orEmpty().ifBlank { null },
                        )
                    }
                    found.addAll(list)
                }
            }
            // 不等待所有站点：个别 JS 源会卡死，最多等 6 秒后返回已拿到的结果。
            withTimeoutOrNull(6_000) { jobs.awaitAll() }
        }
        val results = found.toList().distinctBy { "${it.key}:${it.id}" }
            .sortedByDescending { Trans.t2s(it.title) == key }
        if (results.isNotEmpty()) {
            SearchResultCache.items = (results.map { item ->
                MediaCard(
                    key = item.key,
                    id = item.id,
                    title = item.title,
                    subtitle = item.subtitle,
                    posterUrl = item.posterUrl,
                )
            } + SearchResultCache.items).distinctBy { "${it.key}:${it.id}" }
        }
        results
    }
}
