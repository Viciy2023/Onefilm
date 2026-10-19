package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.SourceDataSource
import com.onefilm.android.tv.compose.bridge.SourceItem
import com.github.catvod.utils.Trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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
        val results = coroutineScope {
            sites.map { site ->
                async {
                    runCatching {
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
                }
            }.awaitAll().flatten()
        }
        results.distinctBy { "${it.key}:${it.id}" }
            .sortedByDescending { Trans.t2s(it.title) == key }
    }
}
