package com.onefilm.android.tv.compose.feature.player


import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.media3.ui.danmaku.DanmakuConfig
import androidx.media3.ui.danmaku.DanmakuParser
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DanmakuDataSource
import com.onefilm.android.tv.core.danmaku.DanmakuQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.stringResource

/** 弹幕开关与渲染：真实调用旧 DanmakuApi 搜索并加载弹幕文件。 */
object DanmakuController {
    private const val TAG = "DanmakuCtrl"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: DanmakuView? = null
    private var xml: String? = null
    private var loadedKey: String = ""
    private var lastPosition: Long = 0L
    private var loadJob: Job? = null
    private var currentKey: String = ""

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
            currentKey = key
            loadedKey = ""
            xml = null
            loadJob?.cancel()
        }
        view = target
        syncSettings()
        Log.d(TAG, "attach view=${target.hashCode()} enabled=$enabled xmlLen=${xml?.length ?: -1}")
        target.setHost(host)
        target.setPlayer(player)
        val cached = xml
        if (cached != null && currentKey.isNotEmpty() && loadedKey == currentKey) {
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

    private fun parseFiltered(content: String): List<androidx.media3.ui.danmaku.DanmakuItem> {
        val items = DanmakuParser.parseXml(content)
        if (blockWords.isEmpty()) return items
        return items.filter { item -> blockWords.none { word -> item.text.contains(word) } }
    }

    fun detach(target: DanmakuView) {
        if (view === target) {
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
        view?.setEnabled(value)
        statusRes = if (value) R.string.common_on else R.string.common_off
    }

    fun toggle(context: Context, key: String, dataSource: DanmakuDataSource) {
        enabled = !enabled
        ComposeDependencies.settingsDataSource.setDanmakuShow(enabled)
        view?.setEnabled(enabled)
        if (enabled && loadedKey != key) load(context, key, dataSource)
        if (!enabled) statusRes = R.string.common_off
    }

    fun ensureLoaded(context: Context, key: String, dataSource: DanmakuDataSource) {
        syncSettings()
        if (key != currentKey) {
            // 换视频/换集：先清空旧弹幕和旧缓存，再发起新的加载。
            currentKey = key
            loadedKey = ""
            xml = null
            loadJob?.cancel()
            view?.setItems(emptyList())
            view?.setEnabled(false)
        }
        Log.d(TAG, "ensureLoaded enabled=$enabled key=$key")
        if (enabled) load(context, key, dataSource) else view?.setEnabled(false)
    }

    private fun load(context: Context, key: String, dataSource: DanmakuDataSource) {
        if (loadedKey == key && xml != null) {
            statusRes = R.string.common_on
            return
        }
        val episode = PlaybackSession.currentEpisode
        val name = PlaybackSession.title.ifBlank { episode?.name.orEmpty() }
        if (name.isBlank()) {
            statusRes = R.string.player_no_source
            return
        }
        val episodeName = episode?.name?.takeIf { it.isNotBlank() && it != name } ?: name
        Log.d(TAG, "load name=$name episode=$episodeName")
        currentKey = key
        loadJob?.cancel()
        loading = true
        statusRes = R.string.settings_loading
        loadJob = scope.launch(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var content: String? = null
            var chosen: List<androidx.media3.ui.danmaku.DanmakuItem> = emptyList()
            outer@ for ((n, e) in DanmakuQuery.combinations(name, episodeName, 6)) {
                if (!isActive) break@outer
                if (System.currentTimeMillis() - startedAt > 25_000L) break@outer
                val sources = try {
                    dataSource.search(n, e)
                } catch (c: CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    emptyList()
                }
                Log.d(TAG, "search name=$n episode=$e sources=${sources.size}")
                for (source in sources.take(8)) {
                    if (!isActive) break@outer
                    val candidate = try {
                        dataSource.load(source.url)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        null
                    }
                    val items = candidate?.let { runCatching { parseFiltered(it) }.getOrDefault(emptyList()) }
                        ?: emptyList()
                    Log.d(TAG, "candidate len=${candidate?.length ?: -1} items=${items.size} url=${source.url}")
                    if (!candidate.isNullOrBlank() && items.isNotEmpty()) {
                        content = candidate
                        chosen = items
                        break@outer
                    }
                }
            }
            Log.d(TAG, "chosen contentLen=${content?.length ?: -1}")
            withContext(Dispatchers.Main) {
                loading = false
                Log.d(TAG, "apply key=$key current=$currentKey view=${view != null} enabled=$enabled")
                if (currentKey != key) return@withContext
                if (content.isNullOrBlank() || chosen.isEmpty()) {
                    xml = null
                    loadedKey = ""
                    view?.setItems(emptyList())
                    view?.setEnabled(false)
                    statusRes = R.string.player_no_source
                    return@withContext
                }
                xml = content
                loadedKey = key
                statusRes = R.string.common_on
                view?.let { target ->
                    runCatching { target.setItems(chosen) }
                    target.setEnabled(enabled)
                    target.setClock(lastPosition)
                }
            }
        }
    }
}
