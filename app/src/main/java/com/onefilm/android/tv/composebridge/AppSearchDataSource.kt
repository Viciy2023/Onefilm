package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.Site
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchProgress
import com.github.catvod.utils.Trans
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
        // 供 Bugly 崩溃回调附带（见 CrashContext），不参与搜索逻辑。
        com.onefilm.android.tv.utils.CrashContext.setConfigUrl(configUrl)
        com.onefilm.android.tv.utils.CrashContext.setLastSearch(key)
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
        // 网盘 / GoProxy 源单独拎出来，排在普通源之后串行跑。
        val heavy = sites.filter { SearchSitePolicy.isNativeHeavy(it) }
        val light = sites.filterNot { SearchSitePolicy.isNativeHeavy(it) }
        val semaphore = Semaphore(SearchSettings.threads())
        val completed = AtomicInteger(0)
        val seen = LinkedHashSet<String>()
        val items = ArrayList<MediaCard>()

        suspend fun runSite(site: Site, timeoutMs: Long) {
            val vods = try {
                withTimeoutOrNull(timeoutMs) {
                    SiteApi.searchContent(site, key, false, page.toString()).list
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null // 失败即跳过，本轮不重试
            }
            val doneCount = completed.incrementAndGet()
            if (vods.isNullOrEmpty()) return
            val snapshot: List<MediaCard>
            synchronized(items) {
                HomeFeedMapper.fromVods(vods, site.key).forEach { card ->
                    if (seen.add("${card.key}:${card.id}")) items.add(card)
                }
                snapshot = items.toList()
            }
            send(SearchProgress(snapshot, false, doneCount, sites.size))
        }

        // 一、普通源并行，结果先出来。
        val jobs = light.map { site ->
            launch(Dispatchers.IO) { semaphore.withPermit { runSite(site, SITE_TIMEOUT_MS) } }
        }
        jobs.forEach { it.join() }

        // 二、网盘 / GoProxy 源最后串行跑。
        // 这类源首次使用要下载并加载 libwexproxy.so（约 10MB），并发会让设备直接 SIGSEGV，
        // 所以这里严格一个一个来；单个源超时或失败直接跳过，不重试。
        if (heavy.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (site in heavy) {
                    runSite(site, HEAVY_TIMEOUT_MS)
                }
            }
        }

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

    private companion object {
        /** 普通源单站超时。 */
        const val SITE_TIMEOUT_MS = 5_000L

        /** 网盘 / GoProxy 源单站超时：要算上下载 + 加载 so 的时间。 */
        const val HEAVY_TIMEOUT_MS = 12_000L
    }
}
