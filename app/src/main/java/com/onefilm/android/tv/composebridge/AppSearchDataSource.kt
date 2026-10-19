package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchProgress
import com.github.catvod.utils.Trans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

class AppSearchDataSource : SearchDataSource {

    private val cache = ConcurrentHashMap<String, List<MediaCard>>()

    override fun search(keyword: String, page: Int): Flow<SearchProgress> = channelFlow {
        VodConfig.get().ensureLoaded()
        val key = Trans.t2s(keyword).trim()
        if (key.isEmpty()) {
            send(SearchProgress(emptyList(), true))
            return@channelFlow
        }
        if (page == 1) cache[key]?.let { send(SearchProgress(it, true)); return@channelFlow }

        val sites = VodConfig.get().getSites().filter { it.isSearchable }
        val seen = LinkedHashSet<String>()
        val items = ArrayList<MediaCard>()

        val jobs = sites.map { site ->
            launch(Dispatchers.IO) {
                runCatching {
                    withTimeout(5_000) { SiteApi.searchContent(site, key, false, page.toString()).list }
                }.onSuccess { vods ->
                    val snapshot: List<MediaCard>
                    synchronized(items) {
                        HomeFeedMapper.fromVods(vods, site.key).forEach { card ->
                            if (seen.add("${card.key}:${card.id}")) items.add(card)
                        }
                        snapshot = items.toList()
                    }
                    send(SearchProgress(snapshot, false))
                }
            }
        }
        jobs.forEach { it.join() }

        val sorted = synchronized(items) {
            items.sortedByDescending { card ->
                val name = Trans.t2s(card.title)
                when {
                    name == key -> 2
                    name.contains(key) -> 1
                    else -> 0
                }
            }
        }
        if (page == 1) {
            cache[key] = sorted
            SearchResultCache.items = sorted
        }
        send(SearchProgress(sorted, true))
    }
}
