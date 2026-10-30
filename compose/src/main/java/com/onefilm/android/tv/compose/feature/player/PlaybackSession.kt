package com.onefilm.android.tv.compose.feature.player

import android.content.Context
import android.view.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.LegacyPlayer
import com.onefilm.android.tv.compose.bridge.LegacyPlayerListener
import com.onefilm.android.tv.compose.bridge.LegacyTrack
import com.onefilm.android.tv.compose.bridge.PlaybackHistoryStore
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlaybackEpisode(val name: String, val url: String)

/**
 * Compose 播放会话：内核统一走旧 PlayerManager（Exo/MPV、解码、轨道、偏移），
 * 这里只维护 UI 状态、选集队列和 History。
 */
object PlaybackSession {
    private const val AUTO_RETRY_MAX = 2
    private var legacy: LegacyPlayer? = null
    private var playerHolder by mutableStateOf<Player?>(null)
    private var currentUrl: String? = null
    private var appContext: Context? = null
    private var openingApplied = false
    private var endingApplied = false
    private var subtitleOff = false
    private var audioIndex = 0
    private var videoIndex = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var historyStore: PlaybackHistoryStore? = null
    var siteKey: String = ""
        private set
    var vodId: String = ""
        private set
    var title: String = ""
        private set
    var poster: String? = null
        private set
    var flagName: String = ""
        private set
    var queue: List<PlaybackEpisode> = emptyList()
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1f)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    // 播放失败时自动重试（同源、同一地址），最多 2 次；成功或换集后重置。
    private var autoRetryLeft = AUTO_RETRY_MAX
    private var autoRetryToken = 0
    // 跨线路回退：详情页把全部 flags 传进来，同源重试仍失败时自动切下一条线路。
    private var alternatives: List<Pair<String, List<PlaybackEpisode>>> = emptyList()
    private var resolverRef: PlaybackResolver? = null
    private var lineSwitchUsed = 0
    var resizeMode by mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT)
        private set
    var softwareDecode by mutableStateOf(false)
        private set
    var textureRender by mutableStateOf(false)
        private set
    var engineMpv by mutableStateOf(false)
        private set
    var subtitleLabel by mutableStateOf("默认")
        private set
    var audioLabel by mutableStateOf("默认")
        private set
    var videoLabel by mutableStateOf("默认")
        private set
    var textOffsetMs by mutableLongStateOf(0L)
        private set
    var audioOffsetMs by mutableLongStateOf(0L)
        private set
    var openingMs by mutableLongStateOf(0L)
        private set
    var endingMs by mutableLongStateOf(0L)
        private set

    val player: Player? get() = playerHolder
    val legacyPlayer: LegacyPlayer? get() = legacy
    val url: String? get() = currentUrl
    val currentEpisode: PlaybackEpisode? get() = queue.getOrNull(currentIndex)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            // 播放器可能自行重连成功（例如刚开始网络没就绪），此时旧的
            // 错误提示必须跟着消失，否则会一直挂在小窗/全屏上。
            if (playing) {
                error = null
                autoRetryLeft = AUTO_RETRY_MAX
                lineSwitchUsed = 0
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                error = null
                durationMs = playerHolder?.duration?.coerceAtLeast(0L) ?: 0L
                refreshTrackLabels()
            }
            if (state == Player.STATE_ENDED) {
                isPlaying = false
                saveHistory()
                // 自然播完也要自动下一集（片尾标记之外的第二道保险）
                val context = appContext
                if (context != null && currentIndex + 1 < queue.size) {
                    scope.launch {
                        playEpisode(context, currentIndex + 1, ComposeDependencies.playbackResolver)
                    }
                }
            }
        }

        override fun onPlayerError(playerError: PlaybackException) {
            isBuffering = false
            val message = playerError.message ?: "播放失败"
            // 同源自动重试：最多 2 次，每次间隔 1.5 秒；仍失败才把错误交给 UI。
            if (autoRetryLeft > 0 && currentUrl != null) {
                autoRetryLeft--
                val attempt = AUTO_RETRY_MAX - autoRetryLeft
                error = "$message（自动重试 $attempt/$AUTO_RETRY_MAX）"
                val token = ++autoRetryToken
                scope.launch {
                    delay(1_500L)
                    if (token == autoRetryToken && error != null) retry()
                }
                return
            }
            // 同源两次重试仍失败：如果详情页提供了其它线路，自动切下一条。
            if (alternatives.size > 1 && lineSwitchUsed < alternatives.size - 1 && tryNextLine()) return
            error = message
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlaybackSession.repeatMode = repeatMode
        }
    }

    private val legacyListener = object : LegacyPlayerListener {
        override fun onPrepare() {
            error = null
            attachPlayer(legacy?.player)
        }

        override fun onTracksChanged() {
            refreshTrackLabels()
        }

        override fun onDecodeChanged() {
            softwareDecode = legacy?.decodeText()?.contains("软") == true
        }

        override fun onError(message: String) {
            error = message
        }

        override fun onPlayerRebuild(player: Player?) {
            engineMpv = legacy?.isMpv() == true
            attachPlayer(player)
        }
    }

    private fun attachPlayer(player: Player?) {
        if (playerHolder !== player) {
            playerHolder?.removeListener(playerListener)
            playerHolder = player
            player?.addListener(playerListener)
        }
        // 播放器重建/切集后必须重新应用倍速，否则界面显示 2x 而实际是 1x
        if (speed != 1f) applySpeed(player)
        isPlaying = player?.isPlaying == true
        repeatMode = player?.repeatMode ?: Player.REPEAT_MODE_OFF
        durationMs = player?.duration?.takeIf { it > 0 } ?: durationMs
    }

    private fun applySpeed(target: Player?) {
        target ?: return
        runCatching { target.playbackParameters = PlaybackParameters(speed) }
    }

    private fun ensureLegacy(): LegacyPlayer? {
        legacy?.let { return it }
        val created = ComposeDependencies.legacyPlayerFactory.create()
        created.listener = legacyListener
        legacy = created
        return created
    }

    fun bindPlayerView(view: PlayerView) {
        legacy?.bindPlayerView(view)
    }

    fun attachMpvSurface(surface: Surface?) {
        legacy?.attachMpvSurface(surface)
    }

    fun isMpv(): Boolean = legacy?.isMpv() == true

    fun setEngine(mpv: Boolean) {
        legacy?.setEngine(mpv)
        engineMpv = mpv
        attachPlayer(legacy?.player)
    }

    fun toggleDecode() {
        legacy?.toggleDecode()
        softwareDecode = legacy?.decodeText()?.contains("软") == true
    }

    val decodeLabel: String get() = legacy?.decodeText() ?: if (softwareDecode) "软解" else "硬解"
    val renderLabel: String get() = if (textureRender) "Texture" else "Surface"
    val resizeLabel: String
        get() = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "裁剪"
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> "拉伸"
            else -> "适应"
        }

    fun toggleRenderMode() {
        textureRender = !textureRender
        legacy?.setRender(if (textureRender) 1 else 0)
    }

    fun cycleResizeMode() {
        resizeMode = when (resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun cycleSubtitle() {
        val tracks = legacy?.tracks()?.filter { it.type == C.TRACK_TYPE_TEXT }.orEmpty()
        if (tracks.isEmpty()) {
            subtitleLabel = "无字幕"
            return
        }
        if (!subtitleOff) {
            val selected = tracks.firstOrNull { it.selected }
            if (selected != null) {
                legacy?.selectTrack(selected.copy(selected = false))
                subtitleOff = true
                subtitleLabel = "关"
                return
            }
        }
        val next = tracks.firstOrNull { !it.selected } ?: tracks.first()
        legacy?.selectTrack(next.copy(selected = true))
        subtitleOff = false
        subtitleLabel = next.name
    }

    val subtitleScale: Float get() = legacy?.subtitleScale() ?: 1f
    val subtitlePosition: Float get() = legacy?.subtitlePosition() ?: 0f
    val subtitleColor: String get() = legacy?.subtitleColorLabel() ?: "白"
    fun cycleSubtitleScale() { legacy?.cycleSubtitleScale() }
    fun cycleSubtitlePosition() { legacy?.cycleSubtitlePosition() }
    fun cycleSubtitleColor() { legacy?.cycleSubtitleColor() }

    fun cycleAudio() {
        val tracks = legacy?.tracks()?.filter { it.type == C.TRACK_TYPE_AUDIO }.orEmpty()
        if (tracks.isEmpty()) {
            audioLabel = "无音轨"
            return
        }
        audioIndex = (audioIndex + 1) % tracks.size
        val next = tracks[audioIndex]
        legacy?.selectTrack(next.copy(selected = true))
        audioLabel = next.name
    }

    fun cycleVideo() {
        val tracks = legacy?.tracks()?.filter { it.type == C.TRACK_TYPE_VIDEO }.orEmpty()
        if (tracks.isEmpty()) {
            videoLabel = "无视轨"
            return
        }
        videoIndex = (videoIndex + 1) % tracks.size
        val next = tracks[videoIndex]
        legacy?.selectTrack(next.copy(selected = true))
        videoLabel = next.name
    }

    fun shiftTextOffset(deltaMs: Long) {
        textOffsetMs = (textOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        legacy?.setTextOffset(textOffsetMs)
    }

    fun shiftAudioOffset(deltaMs: Long) {
        audioOffsetMs = (audioOffsetMs + deltaMs).coerceIn(-10_000L, 10_000L)
        legacy?.setAudioOffset(audioOffsetMs)
    }

    private fun refreshTrackLabels() {
        val tracks = legacy?.tracks().orEmpty()
        val selectedText = tracks.firstOrNull { it.type == C.TRACK_TYPE_TEXT && it.selected }
        subtitleLabel = selectedText?.name ?: "关"
        val selectedAudio = tracks.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.selected }
        audioLabel = selectedAudio?.name ?: "默认"
        val selectedVideo = tracks.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.selected }
        videoLabel = selectedVideo?.name ?: "默认"
    }

    fun setQueue(
        key: String,
        id: String,
        vodTitle: String,
        vodPoster: String?,
        flag: String,
        episodes: List<PlaybackEpisode>,
        index: Int,
    ) {
        siteKey = key
        vodId = id
        title = vodTitle
        poster = vodPoster
        flagName = flag
        queue = episodes
        currentIndex = index.coerceIn(0, (episodes.size - 1).coerceAtLeast(0))
    }

    /** 详情页把全部线路传进来，播放失败时用于自动回退。 */
    fun setAlternatives(list: List<Pair<String, List<PlaybackEpisode>>>) {
        alternatives = list
        lineSwitchUsed = 0
    }

    private fun tryNextLine(): Boolean {
        val ctx = appContext ?: return false
        val resolver = resolverRef ?: return false
        val current = alternatives.indexOfFirst { it.first == flagName }
        if (current < 0) return false
        val next = alternatives.getOrNull(current + 1) ?: return false
        lineSwitchUsed++
        flagName = next.first
        queue = next.second
        currentIndex = currentIndex.coerceIn(0, (next.second.size - 1).coerceAtLeast(0))
        autoRetryLeft = AUTO_RETRY_MAX
        error = "线路不可用，正在切换到「${next.first}」"
        scope.launch { playEpisode(ctx, currentIndex, resolver) }
        return true
    }

    fun play(
        context: Context,
        url: String,
        startMs: Long = 0L,
        headers: Map<String, String> = emptyMap(),
    ) {
        appContext = context.applicationContext
        val target = ensureLegacy() ?: return
        if (currentUrl != url) {
            currentUrl = url
            error = null
            autoRetryLeft = AUTO_RETRY_MAX
            autoRetryToken++
            openingApplied = false
            endingApplied = false
            subtitleOff = false
            audioIndex = 0
            durationMs = 0L
        }
        target.start(url, siteKey, title, startMs, headers)
        engineMpv = target.isMpv()
        attachPlayer(target.player)
        target.player?.play()
    }

    fun playEpisode(context: Context, index: Int, resolver: PlaybackResolver) {
        resolverRef = resolver
        val item = queue.getOrNull(index) ?: return
        val key = siteKey
        val flag = flagName
        error = null
        scope.launch {
            val resolved = resolver.resolve(key, flag, item.url)
            if (resolved.isNullOrBlank()) {
                error = "选集地址解析失败"
                return@launch
            }
            currentIndex = index
            val history = historyStore?.find(key, vodId)
            val start = if (history?.episodeUrl == item.url) {
                maxOf(history.positionMs, history.openingMs)
            } else {
                history?.openingMs ?: 0L
            }
            // 片头/片尾属于整部剧，切换选集后继续沿用。
            if (history != null) {
                setMarks(history.openingMs, history.endingMs)
            } else {
                setMarks(0L, 0L)
            }
            play(context, resolved, start)
            // 切集后播放器可能重建或重置参数，这里必须重新应用倍速
            applySpeed(playerHolder)
            if (history?.episodeUrl == item.url && history.speed > 0f) {
                updateSpeed(history.speed)
            }
            saveHistory()
            preloadNext(resolver)
        }
    }

    /** 解析下一集地址并交给旧 PlayerManager 预加载。 */
    fun preloadNext(resolver: PlaybackResolver) {
        val next = queue.getOrNull(currentIndex + 1) ?: return
        val key = siteKey
        val flag = flagName
        scope.launch {
            val url = resolver.resolve(key, flag, next.url) ?: return@launch
            legacy?.preload(url, key, title)
        }
    }

    fun toggle() {
        val target = playerHolder ?: return
        if (target.isPlaying) {
            target.pause()
            saveHistory()
        } else {
            target.play()
        }
    }

    fun seekTo(positionMs: Long) {
        playerHolder?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val target = playerHolder ?: return
        val duration = target.duration
        val max = if (duration > 0) duration else Long.MAX_VALUE
        target.seekTo((target.currentPosition + deltaMs).coerceIn(0L, max))
    }

    fun updateSpeed(value: Float) {
        speed = value
        playerHolder?.setPlaybackParameters(PlaybackParameters(value))
    }

    fun cycleSpeed() {
        val values = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val next = values[(values.indexOf(speed).takeIf { it >= 0 }?.plus(1) ?: 1) % values.size]
        updateSpeed(next)
    }

    fun retry() {
        val target = legacy ?: return
        val url = currentUrl ?: return
        val position = positionMs()
        error = null
        target.start(url, siteKey, title, position)
        attachPlayer(target.player)
        target.player?.play()
    }

    fun replay() {
        seekTo(0L)
        playerHolder?.play()
    }

    fun refresh() {
        retry()
    }

    fun toggleRepeat() {
        legacy?.toggleRepeat()
        repeatMode = playerHolder?.repeatMode ?: repeatMode
    }

    fun playPrevious(context: Context, resolver: PlaybackResolver) {
        val index = currentIndex - 1
        if (index >= 0) playEpisode(context, index, resolver)
    }

    fun playNext(context: Context, resolver: PlaybackResolver) {
        val index = currentIndex + 1
        if (index < queue.size) playEpisode(context, index, resolver)
    }

    fun positionMs(): Long = playerHolder?.currentPosition?.coerceAtLeast(0L) ?: 0L

    fun setMarks(opening: Long, ending: Long) {
        openingMs = opening.coerceAtLeast(0L)
        endingMs = ending.coerceAtLeast(0L)
        openingApplied = false
        endingApplied = false
    }

    fun markOpening() {
        openingMs = positionMs()
        openingApplied = true
        saveHistory()
    }

    /** 长按片头：清除片头标记。 */
    fun clearOpening() {
        openingMs = 0L
        openingApplied = true
        saveHistory()
    }

    /** 长按片尾：清除片尾标记。 */
    fun clearEnding() {
        endingMs = 0L
        endingApplied = true
        saveHistory()
    }

    fun markEnding() {
        endingMs = positionMs()
        // 按下片尾时当前位置就是片尾起点，必须立刻判定并跳过（旧逻辑把它标记成“已处理”，所以永远不跳）
        endingApplied = false
        saveHistory()
        enforceMarkers()
    }

    fun enforceMarkers() {
        val target = playerHolder ?: return
        if (!openingApplied && openingMs > 1_000L && durationMs > 0L) {
            if (target.currentPosition < openingMs - 1_500L) target.seekTo(openingMs)
            openingApplied = true
        }
        if (!endingApplied && endingMs > 1_000L && target.currentPosition >= endingMs) {
            endingApplied = true
            val context = appContext
            if (context != null && currentIndex + 1 < queue.size) {
                scope.launch {
                    playEpisode(context, currentIndex + 1, ComposeDependencies.playbackResolver)
                }
            } else {
                target.pause()
            }
        }
    }

    fun saveHistory() {
        val item = queue.getOrNull(currentIndex) ?: return
        val position = positionMs()
        val duration = durationMs
        if (position <= 0L || duration <= 0L) return
        historyStore?.save(
            key = siteKey,
            id = vodId,
            title = title,
            poster = poster,
            flag = flagName,
            episodeName = item.name,
            episodeUrl = item.url,
            positionMs = position,
            durationMs = duration,
            speed = speed,
            openingMs = openingMs,
            endingMs = endingMs,
        )
    }

    fun stop() {
        saveHistory()
        playerHolder?.pause()
        playerHolder?.clearMediaItems()
        currentUrl = null
        isPlaying = false
        isBuffering = false
        durationMs = 0L
        error = null
    }

    fun release() {
        saveHistory()
        playerHolder?.removeListener(playerListener)
        playerHolder = null
        legacy?.listener = null
        legacy?.release()
        legacy = null
        currentUrl = null
        isPlaying = false
        isBuffering = false
        durationMs = 0L
        error = null
    }
}
