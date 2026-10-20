package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.DetailCache
import com.onefilm.android.tv.compose.bridge.FavoriteStore
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import com.onefilm.android.tv.compose.bridge.PlaybackHistoryStore
import com.onefilm.android.tv.compose.feature.player.PlaybackEpisode
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DetailRoute(
    key: String,
    id: String,
    previewTitle: String? = null,
    previewPoster: String? = null,
    dataSource: DetailDataSource,
    favoriteStore: FavoriteStore,
    playbackResolver: PlaybackResolver,
    historyStore: PlaybackHistoryStore,
    onBack: () -> Unit,
    onEnterFullscreen: () -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "$key:$id",
        factory = viewModelFactory {
            initializer { DetailViewModel(key, id, dataSource, DetailCache.get(key, id)) }
        },
    )
    val state by viewModel.state.collectAsState()
    var favorite by remember(key, id) { mutableStateOf(favoriteStore.isFavorite(key, id)) }
    var playingUrl by rememberSaveable(key, id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(key, id) {
        if (DetailCache.get(key, id) == null) viewModel.retry()
    }
    var autoStarted by rememberSaveable(key, id) { mutableStateOf(false) }

    fun startPlayback(flagName: String?, episodeUrl: String?) {
        val info = (state as? DetailUiState.Content)?.info ?: return
        val history = historyStore.find(key, id)
        var flag = info.flags.firstOrNull { it.name == flagName }
        var episode = flag?.episodes?.firstOrNull { it.url == episodeUrl }
        if (flag == null && history != null) {
            for (candidate in info.flags) {
                val matched = candidate.episodes.firstOrNull { it.url == history.episodeUrl }
                if (matched != null) {
                    flag = candidate
                    episode = matched
                    break
                }
            }
        }
        if (flag == null) flag = info.flags.firstOrNull()
        if (episode == null) episode = flag?.episodes?.firstOrNull()
        if (flag == null || episode == null) return
        val sourceUrl = episode.url
        if (sourceUrl.isBlank()) return
        val index = flag.episodes.indexOf(episode).coerceAtLeast(0)
        val selectedFlag = flag
        scope.launch {
            val url = playbackResolver.resolve(key, selectedFlag.name, sourceUrl)
            if (!url.isNullOrBlank()) {
                PlaybackSession.setQueue(
                    key = key,
                    id = id,
                    vodTitle = info.title,
                    vodPoster = info.posterUrl,
                    flag = selectedFlag.name,
                    episodes = selectedFlag.episodes.map { PlaybackEpisode(it.name, it.url) },
                    index = index,
                )
                val resumeFromHistory = history?.episodeUrl == sourceUrl
                val start = if (resumeFromHistory) {
                    (history!!.positionMs - 10_000L).coerceAtLeast(0L)
                } else {
                    0L
                }
                if (history != null) {
                    PlaybackSession.setMarks(history.openingMs, history.endingMs)
                } else {
                    PlaybackSession.setMarks(0L, 0L)
                }
                PlaybackSession.play(context, url, start)
                PlaybackSession.preloadNext(playbackResolver)
                if (resumeFromHistory) PlaybackSession.updateSpeed(history!!.speed)
                playingUrl = url
            }
        }
    }

    LaunchedEffect(state, key, id) {
        val info = (state as? DetailUiState.Content)?.info ?: return@LaunchedEffect
        if (autoStarted) return@LaunchedEffect
        autoStarted = true
        if (PlaybackSession.siteKey == key && PlaybackSession.vodId == id && PlaybackSession.player != null) {
            playingUrl = PlaybackSession.url
            return@LaunchedEffect
        }
        delay(350)
        val history = historyStore.find(key, id)
        val flag = history?.let { saved ->
            info.flags.firstOrNull { candidate ->
                candidate.episodes.any { it.url == saved.episodeUrl }
            }
        } ?: info.flags.firstOrNull()
        val episode = flag?.episodes?.firstOrNull { it.url == history?.episodeUrl }
            ?: flag?.episodes?.firstOrNull()
        if (flag != null && episode != null) {
            startPlayback(flag.name, episode.url)
        }
    }

    DetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onPlay = { flagName, episodeUrl -> startPlayback(flagName, episodeUrl) },
        playingUrl = playingUrl,
        onExpandPlayer = onEnterFullscreen,
        previewTitle = previewTitle,
        previewPoster = previewPoster,
        playingEpisodeUrl = if (PlaybackSession.siteKey == key && PlaybackSession.vodId == id) {
            PlaybackSession.currentEpisode?.url
        } else {
            null
        },
        favorite = favorite,
        onToggleFavorite = {
            val info = (state as? DetailUiState.Content)?.info
            favoriteStore.toggle(
                key = key,
                id = id,
                siteName = "",
                title = info?.title.orEmpty(),
                posterUrl = info?.posterUrl,
            )
            favorite = !favorite
        },
    )
}
