package com.onefilm.android.tv.compose.feature.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.danmaku.DanmakuConfig
import androidx.media3.ui.danmaku.DanmakuItem
import androidx.media3.ui.danmaku.DanmakuParser
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DanmakuDataSource
import com.onefilm.android.tv.compose.bridge.DanmakuSource
import com.onefilm.android.tv.core.danmaku.DanmakuQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 弹幕加载与渲染，逻辑对齐上游 FongMi：
 * - 每次切集只查一次，name=影片基础名，episode=数字集名（电影 1，1080/2160 不算集号）；
 * - 服务器返回的源按 年份/片名 打分，同分再按平台速度与集数匹配排序，取前 3 条校验；
 * - 单源失败时对同一 URL 重试一次（命中服务端缓存只需几秒）；
 * - 同一 key 有任务在跑时不重复加载；退出/切集取消。
 */
object DanmakuController {
    private const val TAG = "DanmakuCtrl"
    private const val SEARCH_TIMEOUT_MS = 60_000L
    private const val LOAD_TIMEOUT_MS = 45_000L
    private const val RETRY_DELAY_MS = 500L
    /** 同一部剧的源列表缓存 10 分钟；换集只重选源，不重复搜索。 */
    private const val SOURCE_CACHE_TTL_MS = 10 * 60 * 1000L
    /** 每个平台最多 1 条，最多 6 条：bilibili > 360 > youku > iqiyi > tencent > migu。 */
    private const val MAX_SOURCES = 6

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: DanmakuView? = null
    private var xml: String? = null
    private var loadedKey: String = ""
    private var lastPosition: Long = 0L
    private var loadJob: Job? = null
    private var currentKey: String = ""
    // 同一部剧的源列表缓存：换集只重新选源，不再重复搜索。
    @Volatile private var cachedShow: String = ""
    @Volatile private var cachedSources: List<DanmakuSource> = emptyList()
    @Volatile private var cachedAt: Long = 0L
    private var canSearch = true

    var enabled by mutableStateOf(true)
        private set
    var loading by mutableStateOf(false)
        private set
    var statusRes by mutableIntStateOf(R.string.common_default)
        private set
    var densityLines by mutableIntStateOf(8)
        private set
    var transparency by mutableFloatStateOf(0f)
        private set
    var textScale by mutableFloatStateOf(1f)
        private set
    private var durationMs by mutableLongStateOf(8000L)
    private var blockWords: List<String> = emptyList()
    private var engineConfig: DanmakuConfig? = null

    private fun syncSettings() {
        val dataSource = ComposeDependencies.danmakuDataSource
        val config = dataSource.displayConfig()
        enabled = config.show
        canSearch = config.show && config.load && config.auto && config.apiConfigured
        textScale = config.scale
        transparency = config.transparency
        densityLines = config.lines
        durationMs = config.durationMs
        blockWords = config.blockWords
        engineConfig = runCatching { dataSource.engineConfig() }.getOrNull()
        applyConfig()
    }

    private fun applyConfig() {
        val full = engineConfig
        if (full != null) {
            view?.setConfig(full)
            return
        }
        view?.setConfig(
            DanmakuConfig.Builder()
                .setTextScale(textScale)
                .setTransparency(transparency)
                .setDurationMs(durationMs)
                .setMaxScrollLines(densityLines)
                .build(),
        )
    }

    fun cycleDensity() {
        val next = when (densityLines) {
            4 -> 8
            8 -> 12
            else -> 4
        }
        ComposeDependencies.settingsDataSource.setDanmakuLines(next)
        densityLines = next
        syncSettings()
    }

    fun cycleTransparency() {
        val next = when {
            transparency < 0.1f -> 0.25f
            transparency < 0.3f -> 0.5f
            transparency < 0.6f -> 0.7f
            else -> 0f
        }
        ComposeDependencies.settingsDataSource.setDanmakuTransparency(next)
        transparency = next
        syncSettings()
    }

    fun cycleScale() {
        val next = when {
            textScale < 0.9f -> 1.0f
            textScale < 1.1f -> 1.2f
            textScale < 1.3f -> 1.4f
            else -> 0.8f
        }
        ComposeDependencies.settingsDataSource.setDanmakuScale(next)
        textScale = next
        syncSettings()
    }

    fun attach(target: DanmakuView, host: PlayerView? = null, player: Player? = null, key: String? = null) {
        if (key != null && key != currentKey) {
            // 新视频/新集：立即丢弃上一部的缓存与弹幕，避免串台。
            cancelLoad()
            currentKey = key
            loadedKey = ""
            xml = null
        }
        view = target
        syncSettings()
        Log.d(TAG, "attach view=${target.hashCode()} enabled=$enabled canSearch=$canSearch xmlLen=${xml?.length ?: -1}")
        target.setHost(host)
        target.setPlayer(player)
        val cached = xml
        if (cached != null && canSearch && currentKey.isNotEmpty() && loadedKey == currentKey) {
            runCatching { target.setItems(parseFiltered(cached)) }
            target.setEnabled(enabled)
        } else {
            target.setItems(emptyList())
            target.setEnabled(false)
        }
        target.setClock(lastPosition)
    }

    /** 播放器切换（Exo/MPV）时同步给弹幕视图。 */
    fun setPlayer(player: Player?) {
        view?.setPlayer(player)
    }

    private fun parseFiltered(content: String): List<DanmakuItem> {
        val items = DanmakuParser.parseXml(content)
        if (blockWords.isEmpty()) return items
        return items.filter { item -> blockWords.none { word -> item.text.contains(word) } }
    }

    fun detach(target: DanmakuView) {
        if (view === target) {
            cancelLoad()
            view = null
            target.setHost(null)
            target.setPlayer(null)
        }
    }

    fun attachedTo(target: DanmakuView): Boolean = view === target

    fun setClock(positionMs: Long) {
        lastPosition = positionMs
        view?.setClock(positionMs)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        ComposeDependencies.settingsDataSource.setDanmakuShow(value)
        view?.setEnabled(value && canSearch)
        statusRes = if (value) R.string.common_on else R.string.common_off
    }

    fun toggle(context: Context, key: String, dataSource: DanmakuDataSource) {
        enabled = !enabled
        ComposeDependencies.settingsDataSource.setDanmakuShow(enabled)
        view?.setEnabled(enabled && canSearch)
        if (enabled) {
            ensureLoaded(context, key, dataSource)
        } else {
            cancelLoad()
            statusRes = R.string.common_off
        }
    }

    fun ensureLoaded(context: Context, key: String, dataSource: DanmakuDataSource) {
        syncSettings()
        if (!canSearch) {
            cancelLoad()
            view?.setItems(emptyList())
            view?.setEnabled(false)
            statusRes = if (!enabled) R.string.common_off else R.string.player_no_source
            return
        }
        if (key != currentKey) {
            // 换视频/换集：先清空旧弹幕和旧缓存，再发起新的加载。
            cancelLoad()
            currentKey = key
            loadedKey = ""
            xml = null
            view?.setItems(emptyList())
            view?.setEnabled(false)
        } else if (loadJob?.isActive == true) {
            // 同一 key 已有任务在跑，视图重建时不重复取消/重发。
            Log.d(TAG, "ensureLoaded skip in-flight key=$key")
            return
        }
        Log.d(TAG, "ensureLoaded enabled=$enabled canSearch=$canSearch key=$key")
        if (loadedKey == key && xml != null) {
            statusRes = R.string.common_on
            return
        }
        load(context, key, dataSource)
    }

    private fun cancelLoad() {
        loadJob?.cancel()
        loadJob = null
        loading = false
        runCatching { ComposeDependencies.danmakuDataSource.cancel() }
    }

    /**
     * 换集复用：同一部剧（片名+年份）且缓存未过期，并且缓存里确实有当前集。
     * 电影没有“第N集”标记时视为可复用。
     */
    private fun canReuseSources(showKey: String, episode: String): Boolean {
        if (cachedShow != showKey || cachedSources.isEmpty()) return false
        if (SystemClock.elapsedRealtime() - cachedAt >= SOURCE_CACHE_TTL_MS) return false
        var marked = false
        for (source in cachedSources) {
            val number = DanmakuQuery.episodeOf(source.name) ?: continue
            marked = true
            if (number.toString() == episode) return true
        }
        return !marked
    }

    private suspend fun fetchSource(dataSource: DanmakuDataSource, url: String): String? = try {
        withTimeout(LOAD_TIMEOUT_MS) { dataSource.load(url) }
    } catch (t: TimeoutCancellationException) {
        Log.d(TAG, "load timeout url=$url")
        null
    } catch (c: CancellationException) {
        throw c
    } catch (t: Throwable) {
        Log.d(TAG, "load error url=$url ${t.message}")
        null
    }

    private fun load(context: Context, key: String, dataSource: DanmakuDataSource) {
        val episode = PlaybackSession.currentEpisode
        val rawName = PlaybackSession.title.ifBlank { episode?.name.orEmpty() }
        if (rawName.isBlank()) {
            statusRes = R.string.player_no_source
            return
        }
        val name = DanmakuQuery.bestName(rawName)
        val fallback = (PlaybackSession.currentIndex + 1).coerceAtLeast(1)
        val episodeQuery = DanmakuQuery.bestEpisode(episode?.name.orEmpty(), fallback)
        val year = PlaybackSession.vodYear.ifBlank { DanmakuQuery.yearOf(rawName).orEmpty() }
        Log.d(TAG, "load name=$name episode=$episodeQuery year=$year key=$key")
        currentKey = key
        loadJob?.cancel()
        loading = true
        statusRes = R.string.settings_loading
        val showKey = "$name|$year"
        loadJob = scope.launch(Dispatchers.IO) {
            val sources = if (canReuseSources(showKey, episodeQuery)) {
                Log.d(TAG, "reuse sources=${cachedSources.size} show=$showKey episode=$episodeQuery")
                cachedSources
            } else {
                val fetched = try {
                    withTimeout(SEARCH_TIMEOUT_MS) { dataSource.search(name, episodeQuery) }
                } catch (t: TimeoutCancellationException) {
                    Log.d(TAG, "search timeout name=$name episode=$episodeQuery")
                    emptyList()
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    emptyList()
                }
                Log.d(TAG, "search name=$name episode=$episodeQuery sources=${fetched.size}")
                if (fetched.isNotEmpty()) {
                    cachedShow = showKey
                    cachedSources = fetched
                    cachedAt = SystemClock.elapsedRealtime()
                }
                fetched
            }
            if (sources.isNotEmpty()) {
                val ranked = sources.sortedByDescending {
                    DanmakuQuery.scoreSource(it.name, year, name, episodeQuery)
                }
                // 只保留“最匹配那一档”的源，避免为了凑平台而拿到错误版本；
                // 每个平台最多 1 条，再按 bilibili > 360 > youku > iqiyi > tencent > migu 排序，最多 6 条。
                val bestBase = DanmakuQuery.baseScore(ranked[0].name, year, name, episodeQuery)
                val byPlatform = LinkedHashMap<String, DanmakuSource>()
                for (candidate in ranked) {
                    val base = DanmakuQuery.baseScore(candidate.name, year, name, episodeQuery)
                    if (base < bestBase - 400) break
                    val platform = DanmakuQuery.platformKey(candidate.name).ifBlank { candidate.name }
                    if (!byPlatform.containsKey(platform)) byPlatform[platform] = candidate
                }
                val ordered = byPlatform.values
                    .sortedBy { DanmakuQuery.platformRank(it.name) }
                    .take(MAX_SOURCES)
                Log.d(TAG, "sources=${sources.size} picked=${ordered.size} platforms=${byPlatform.keys}")
                for (source in ordered) {
                    val score = DanmakuQuery.scoreSource(source.name, year, name, episodeQuery)
                    Log.d(TAG, "source score=$score platform=${DanmakuQuery.platformOf(source.name)} name=${source.name}")
                    var content = fetchSource(dataSource, source.url)
                    if (content.isNullOrBlank()) {
                        // 第一次超时/失败时，服务端通常已把结果写进缓存；同一 URL 再要一次只需几秒。
                        Log.d(TAG, "retry same url=${source.url}")
                        delay(RETRY_DELAY_MS)
                        content = fetchSource(dataSource, source.url)
                    }
                    val items = content
                        ?.let { runCatching { parseFiltered(it) }.getOrDefault(emptyList()) }
                        ?: emptyList()
                    Log.d(TAG, "candidate len=${content?.length ?: -1} items=${items.size} url=${source.url}")
                    if (!content.isNullOrBlank() && items.isNotEmpty()) {
                        applySuccess(key, content, items)
                        return@launch
                    }
                }
            }
            applyFailure(key)
        }
    }

    private suspend fun applySuccess(key: String, content: String, items: List<DanmakuItem>) {
        withContext(Dispatchers.Main) {
            if (currentKey != key) return@withContext
            loading = false
            xml = content
            loadedKey = key
            statusRes = R.string.common_on
            view?.let { target ->
                runCatching { target.setItems(items) }
                target.setEnabled(enabled)
                target.setClock(lastPosition)
            }
        }
    }

    private suspend fun applyFailure(key: String) {
        withContext(Dispatchers.Main) {
            if (currentKey != key) return@withContext
            loading = false
            xml = null
            loadedKey = ""
            view?.setItems(emptyList())
            view?.setEnabled(false)
            statusRes = R.string.player_no_source
        }
    }
}
