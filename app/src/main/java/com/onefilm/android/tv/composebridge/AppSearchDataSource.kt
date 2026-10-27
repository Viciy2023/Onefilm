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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class AppSearchDataSource : SearchDataSource {

    private val cache = ConcurrentHashMap<String, List<MediaCard>>()

    override fun search(keyword: String, page: Int): Flow<SearchProgress> = channelFlow {
        // Config parsing/network must never run on the Compose main thread.
        withContext(Dispatchers.IO) { VodConfig.get().ensureLoaded() }
        val key = Trans.t2s(keyword).trim()
        if (key.isEmpty()) {
            send(SearchProgress(emptyList(), true))
            return@channelFlow
        }
        val configUrl = VodConfig.getUrl().orEmpty()
        // 站点勾选变化后必须重新搜索，不能命中旧站点集合的缓存。
        val cacheKey = key + "@" + SearchSettings.scopeSignature(configUrl)
        if (page == 1) {
            cache[cacheKey]?.let { send(SearchProgress(it, true)); return@channelFlow }
            DiskCache.read<List<MediaCard>>("search_${cacheKey.hashCode()}", 10 * 60_000L)?.let {
                cache[cacheKey] = it
                send(SearchProgress(it, true))
                return@channelFlow
            }
        }

        // quickSearch 源优先，其余源继续并行；返回一个追加一个。
        val disabledSites = SearchSettings.disabledSites(configUrl)
        val sites = VodConfig.get().getSites()
            .filter { it.isSearchable && it.key !in disabledSites }
            .sortedByDescending { it.isQuickSearch }
        val semaphore = Semaphore(SearchSettings.threads())
        val completed = AtomicInteger(0)
        val seen = LinkedHashSet<String>()
        val items = ArrayList<MediaCard>()

        val jobs = sites.map { site ->
            launch(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        withTimeout(5_000) { SiteApi.searchContent(site, key, false, page.toString()).list }
                    }.onSuccess { vods ->
                        val snapshot: List<MediaCard>
                        val doneCount = completed.incrementAndGet()
                        synchronized(items) {
                            HomeFeedMapper.fromVods(vods, site.key).forEach { card ->
                                if (seen.add("${card.key}:${card.id}")) items.add(card)
                            }
                            snapshot = items.toList()
                        }
                        send(SearchProgress(snapshot, false, doneCount, sites.size))
                    }.onFailure {
                        completed.incrementAndGet()
                    }
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
            cache[cacheKey] = sorted
            SearchResultCache.items = sorted
            DiskCache.write("search_${cacheKey.hashCode()}", sorted)
        }
        send(SearchProgress(sorted, true, sites.size, sites.size))
    }
}
