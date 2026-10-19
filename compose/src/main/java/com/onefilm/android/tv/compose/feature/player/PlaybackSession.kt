package com.onefilm.android.tv.compose.feature.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.onefilm.android.tv.compose.bridge.PlaybackHistoryStore
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class PlaybackEpisode(val name: String, val url: String)

/**
 * 进程内共享播放会话：详情页小窗与全屏共用同一个 ExoPlayer，
 * 同时保存选集队列、倍速和旧版 History 所需的影片信息。
 */
object PlaybackSession {
    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
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

    val exoPlayer: ExoPlayer? get() = player
    val url: String? get() = currentUrl
    val currentEpisode: PlaybackEpisode? get() = queue.getOrNull(currentIndex)

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                durationMs = player?.duration?.coerceAtLeast(0L) ?: 0L
            }
            if (state == Player.STATE_ENDED) {
                isPlaying = false
                saveHistory()
            }
        }

        override fun onPlayerError(playerError: PlaybackException) {
            error = playerError.message ?: "播放失败"
            isBuffering = false
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            this@PlaybackSession.repeatMode = repeatMode
        }
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

    fun play(context: Context, url: String, startMs: Long = 0L) {
        val appContext = context.applicationContext
        val target = player ?: ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
            it.playWhenReady = true
            it.setPlaybackParameters(PlaybackParameters(speed))
            player = it
        }
        if (currentUrl != url) {
            currentUrl = url
            error = null
            target.setMediaItem(MediaItem.fromUri(url))
            target.prepare()
            if (startMs > 0L) target.seekTo(startMs)
        } else if (startMs > 0L && kotlin.math.abs(target.currentPosition - startMs) > 2_000L) {
            target.seekTo(startMs)
        }
        target.play()
    }

    fun playEpisode(context: Context, index: Int, resolver: PlaybackResolver) {
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
            val start = if (history?.episodeUrl == item.url) history.positionMs else 0L
            play(context, resolved, start)
            if (history?.episodeUrl == item.url && history.speed > 0f) {
                updateSpeed(history.speed)
            }
            saveHistory()
        }
    }

    fun toggle() {
        val target = player ?: return
        if (target.isPlaying) {
            target.pause()
            saveHistory()
        } else {
            target.play()
        }
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val target = player ?: return
        val duration = target.duration
        val max = if (duration > 0) duration else Long.MAX_VALUE
        target.seekTo((target.currentPosition + deltaMs).coerceIn(0L, max))
    }

    fun updateSpeed(value: Float) {
        speed = value
        player?.setPlaybackParameters(PlaybackParameters(value))
    }

    fun retry() {
        val target = player ?: return
        error = null
        target.prepare()
        target.play()
    }

    fun replay() {
        seekTo(0L)
        player?.play()
    }

    fun refresh() {
        retry()
    }

    fun toggleRepeat() {
        val target = player ?: return
        target.repeatMode = when (target.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun playPrevious(context: Context, resolver: PlaybackResolver) {
        val index = currentIndex - 1
        if (index >= 0) playEpisode(context, index, resolver)
    }

    fun playNext(context: Context, resolver: PlaybackResolver) {
        val index = currentIndex + 1
        if (index < queue.size) playEpisode(context, index, resolver)
    }

    fun positionMs(): Long = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

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
        )
    }

    fun stop() {
        saveHistory()
        player?.pause()
        player?.clearMediaItems()
        currentUrl = null
        isPlaying = false
        isBuffering = false
        durationMs = 0L
        error = null
    }

    fun release() {
        saveHistory()
        player?.release()
        player = null
        currentUrl = null
        isPlaying = false
        isBuffering = false
        durationMs = 0L
        error = null
    }
}
