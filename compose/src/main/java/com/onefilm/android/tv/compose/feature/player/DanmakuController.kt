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

    private fun syncSettings() {
        val config = ComposeDependencies.danmakuDataSource.displayConfig()
        enabled = config.show
        textScale = config.scale
        transparency = config.transparency
        densityLines = config.lines
        durationMs = config.durationMs
        blockWords = config.blockWords
        applyConfig()
    }

    private fun applyConfig() {
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
        densityLines = when (densityLines) {
            4 -> 8
            8 -> 12
            else -> 4
        }
        applyConfig()
    }

    fun cycleTransparency() {
        transparency = when {
            transparency < 0.1f -> 0.25f
            transparency < 0.3f -> 0.5f
            transparency < 0.6f -> 0.7f
            else -> 0f
        }
        applyConfig()
    }

    fun cycleScale() {
        textScale = when {
            textScale < 0.9f -> 1.0f
            textScale < 1.1f -> 1.2f
            textScale < 1.3f -> 1.4f
            else -> 0.8f
        }
        applyConfig()
    }

    fun attach(target: DanmakuView, host: PlayerView? = null, player: Player? = null) {
        view = target
        syncSettings()
        Log.d(TAG, "attach view=${target.hashCode()} enabled=$enabled xmlLen=${xml?.length ?: -1}")
        target.setHost(host)
        target.setPlayer(player)
        xml?.let { runCatching { target.setItems(parseFiltered(it)) } }
        target.setEnabled(enabled)
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
        view?.setEnabled(value)
        statusRes = if (value) R.string.common_on else R.string.common_off
    }

    fun toggle(context: Context, key: String, dataSource: DanmakuDataSource) {
        enabled = !enabled
        view?.setEnabled(enabled)
        if (enabled && loadedKey != key) load(context, key, dataSource)
        if (!enabled) statusRes = R.string.common_off
    }

    fun ensureLoaded(context: Context, key: String, dataSource: DanmakuDataSource) {
        syncSettings()
        Log.d(TAG, "ensureLoaded enabled=$enabled key=$key")
        if (enabled) load(context, key, dataSource)
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
                if (System.currentTimeMillis() - startedAt > 25_000L) break@outer
                val sources = runCatching { dataSource.search(n, e) }.getOrDefault(emptyList())
                Log.d(TAG, "search name=$n episode=$e sources=${sources.size}")
                for (source in sources.take(8)) {
                    val candidate = runCatching { dataSource.load(source.url) }.getOrNull()
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
                    enabled = false
                    view?.setEnabled(false)
                    statusRes = R.string.player_no_source
                    return@withContext
                }
                xml = content
                loadedKey = key
                statusRes = R.string.common_on
                view?.let { target ->
                    runCatching { target.setItems(chosen) }
                    target.setEnabled(true)
                    target.setClock(lastPosition)
                }
            }
        }
    }
}
