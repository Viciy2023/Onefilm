package com.onefilm.android.tv.compose.feature.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.media3.ui.danmaku.DanmakuConfig
import androidx.media3.ui.danmaku.DanmakuParser
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.bridge.DanmakuDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 弹幕开关与渲染：真实调用旧 DanmakuApi 搜索并加载弹幕文件。 */
object DanmakuController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var view: DanmakuView? = null
    private var xml: String? = null
    private var loadedKey: String = ""
    private var lastPosition: Long = 0L

    var enabled by mutableStateOf(true)
        private set
    var loading by mutableStateOf(false)
        private set
    var status by mutableStateOf("默认")
        private set
    var densityLines by mutableIntStateOf(8)
        private set
    var transparency by mutableFloatStateOf(0f)
        private set
    var textScale by mutableFloatStateOf(1f)
        private set

    private fun applyConfig() {
        view?.setConfig(
            DanmakuConfig.Builder()
                .setTextScale(textScale)
                .setTransparency(transparency)
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

    fun attach(target: DanmakuView) {
        view = target
        applyConfig()
        xml?.let { runCatching { target.setItems(DanmakuParser.parseXml(it)) } }
        target.setEnabled(enabled)
        target.setClock(lastPosition)
    }

    fun detach(target: DanmakuView) {
        if (view === target) view = null
    }

    fun attachedTo(target: DanmakuView): Boolean = view === target

    fun setClock(positionMs: Long) {
        lastPosition = positionMs
        view?.setClock(positionMs)
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        view?.setEnabled(value)
        status = if (value) "开" else "关"
    }

    fun toggle(context: Context, key: String, dataSource: DanmakuDataSource) {
        enabled = !enabled
        view?.setEnabled(enabled)
        if (enabled && loadedKey != key) load(context, key, dataSource)
        if (!enabled) status = "关"
    }

    fun ensureLoaded(context: Context, key: String, dataSource: DanmakuDataSource) {
        if (enabled) load(context, key, dataSource)
    }

    private fun load(context: Context, key: String, dataSource: DanmakuDataSource) {
        if (loading) return
        if (loadedKey == key && xml != null) {
            status = "开"
            return
        }
        val episode = PlaybackSession.currentEpisode
        val name = PlaybackSession.title
        if (name.isBlank() || episode == null) {
            status = "无源"
            return
        }
        loading = true
        status = "加载中"
        scope.launch {
            val sources = runCatching { dataSource.search(name, episode.name) }.getOrDefault(emptyList())
            val content = sources.firstOrNull()?.let { source ->
                runCatching { dataSource.load(source.url) }.getOrNull()
            }
            loading = false
            if (content.isNullOrBlank()) {
                enabled = false
                view?.setEnabled(false)
                status = "无源"
                return@launch
            }
            xml = content
            loadedKey = key
            status = "开"
            view?.let { target ->
                runCatching { target.setItems(DanmakuParser.parseXml(content)) }
                target.setEnabled(true)
                target.setClock(lastPosition)
            }
        }
    }
}
