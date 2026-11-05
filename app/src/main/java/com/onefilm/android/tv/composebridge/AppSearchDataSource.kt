package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.api.loader.BaseLoader
import com.onefilm.android.tv.bean.Site
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchProgress
import com.onefilm.android.tv.utils.Task
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class AppSearchDataSource : SearchDataSource {

    /** 单站点搜索的执行线程池：卡死的线程可以被放弃，而不阻塞整轮搜索。 */
    private val siteExecutor = java.util.concurrent.Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "search-site").apply { isDaemon = true }
    }

    /** 本次进程内已确认会卡死的站点，后续搜索直接跳过，避免线程持续堆积。 */
    private val blockedSites: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    /** 各站点的连续超时次数：连续 2 次才判定为卡死，避免偶发慢站被永久跳过。 */
    private val timeoutCounts: java.util.concurrent.ConcurrentHashMap<String, Int> = java.util.concurrent.ConcurrentHashMap()

    /** JS 源单站超时：QuickJS 引擎初始化 + 加载模块本身就较重，5 秒不够。 */
    private val JS_SITE_TIMEOUT_MS = 15_000L

    /** Python 源单站超时：Chaquopy 首次调用要加载解释器与依赖，明显更慢。 */
    private val PY_SITE_TIMEOUT_MS = 25_000L

    /** jar（csp_*）源单站超时：要加载 dex/so。 */
    private val JAR_SITE_TIMEOUT_MS = 20_000L

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
            if (blockedSites.contains(site.key)) {
                dbg("skip-blocked site=${site.key}")
                completed.incrementAndGet()
                return
            }
            // 个别源的 JS 是阻塞式原生调用或死循环，协程取消撤不掉它。
            // 放到线程池 + Future.get(超时) 才能保证本轮一定往下走；
            // 超时站点记入 blockedSites，本次进程内不再重试，避免线程持续堆积。
            val future = siteExecutor.submit(java.util.concurrent.Callable {
                SiteApi.searchContent(site, key, false, page.toString()).list
            })
            val isJs = site.api?.contains(".js") == true
            val api = site.api ?: ""
            val effectiveTimeout = when {
                isJs -> JS_SITE_TIMEOUT_MS
                api.contains(".py") -> PY_SITE_TIMEOUT_MS
                api.startsWith("csp_") -> JAR_SITE_TIMEOUT_MS
                else -> timeoutMs
            }
            var timedOut = false
            val vods = try {
                future.get(effectiveTimeout, TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                timedOut = true
                future.cancel(true)
                val strikes = (timeoutCounts.merge(site.key, 1) { a, b -> a + b } ?: 1)
                if (strikes >= 2) blockedSites.add(site.key)
                dbg("timeout site=${site.key} ms=$effectiveTimeout strikes=$strikes")
                null
            } catch (e: CancellationException) {
                future.cancel(true)
                throw e
            } catch (e: Throwable) {
                null // 失败即跳过，本轮不重试
            }
            // 方案 B：JS 站点本轮只用这一次，搜索返回后释放其引擎，
            // 避免 80+ 个 QuickJS 运行时同时常驻。延迟 8 秒释放（大于 JsLoader 内 5 秒保护窗口），
            // 避开“用户此刻正好点开该站点详情”的竞态。
            if (!timedOut) timeoutCounts.remove(site.key)
            // 超时的站点其调用可能还在跑（init 未完成），此时不能回收，否则会把引擎掐断。
            if (isJs && !timedOut) {
                val siteKey = site.key
                Task.schedule({
                    val ok = runCatching { BaseLoader.get().releaseJs(siteKey) }.getOrDefault(false)
                    dbg("release site=$siteKey ok=$ok")
                }, 8, TimeUnit.SECONDS)
            }
            dbg("site=${site.key} js=${site.api?.contains(".js") == true} vods=${vods?.size}")
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
        val jsTotal = sites.count { it.api?.contains(".js") == true }
        dbg("search start keyword=$key sites=${sites.size} js=$jsTotal threads=${SearchSettings.threads()}")
        val jobs = light.map { site ->
            launch(Dispatchers.IO) { semaphore.withPermit { runSite(site, SITE_TIMEOUT_MS) } }
        }
        // 个别源的 JS 调用是阻塞的、不响应协程取消，join 可能永远挂住（表现为计数停在 25x/26x）。
        // 加总超时兜底，保证后续回收与 done 一定执行。
        val lightDone = withTimeoutOrNull(LIGHT_TOTAL_TIMEOUT_MS) {
            jobs.forEach { it.join() }
            true
        } ?: false
        dbg("light done=$lightDone completed=${completed.get()}/${sites.size} js=$jsTotal")

        // 普通（JS）源已全部跑完，立刻回收引擎：95 个 QuickJS 运行时约 600MB，
        // 是 2GB 设备上最大的常驻开销；后面的重型源都是 csp_* jar 类，不需要 JS 引擎。
        trimJsEngines()
        // 20 秒宽限期内的引擎会在这次补刀里释放。
        Task.schedule({ trimJsEngines() }, 25, TimeUnit.SECONDS)

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
        // 兜底再回收一次：覆盖重型源阶段又被建出来的引擎（通常为 0）。
        trimJsEngines()
    }

    /** 回收聚合搜索期间创建的 JS 引擎，只保留首页站点；被回收站点在下次使用时按需重建。 */
    private fun trimJsEngines() {
        Task.execute {
            runCatching {
                val keep = HashSet<String>()
                val homeKey = VodConfig.get().getHome().key
                if (!homeKey.isNullOrBlank()) keep.add(homeKey)
                val released = BaseLoader.get().retainJs(keep)
                dbg("trim released=$released initFailures=${com.onefilm.android.tv.api.loader.JsLoader.initFailures()}")
            }
        }
    }

    /** 临时诊断：写文件，避免被 logcat 缓冲区冲掉。 */
    private fun dbg(msg: String) {
        com.onefilm.android.tv.utils.SearchDebug.log(msg)
    }

    private companion object {
        /** 普通源单站超时。 */
        const val SITE_TIMEOUT_MS = 5_000L

        /** 普通源整体兜底超时：个别源的 JS 调用是阻塞的，join 可能永远不返回。 */
        const val LIGHT_TOTAL_TIMEOUT_MS = 120_000L

        /** 网盘 / GoProxy 源单站超时：要算上下载 + 加载 so 的时间。 */
        const val HEAVY_TIMEOUT_MS = 12_000L
    }
}
