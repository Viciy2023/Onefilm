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
    DetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onPlay = { flagName, episodeUrl ->
            val info = (state as? DetailUiState.Content)?.info
            if (info == null) return@DetailScreen
            val history = historyStore.find(key, id)
            var flag = info.flags.firstOrNull { it.name == flagName }
            var episode = flag?.episodes?.firstOrNull { it.url == episodeUrl }
            if (flagName == null && history != null) {
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
            if (flag == null || episode == null) return@DetailScreen
            val sourceUrl = episode.url
            val index = flag.episodes.indexOf(episode).coerceAtLeast(0)
            val selectedFlag = flag
            if (sourceUrl.isBlank()) return@DetailScreen
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
                    val start = if (history?.episodeUrl == sourceUrl) history.positionMs else 0L
                    if (history?.episodeUrl == sourceUrl) {
                        PlaybackSession.setMarks(history.openingMs, history.endingMs)
                    } else {
                        PlaybackSession.setMarks(0L, 0L)
                    }
                    PlaybackSession.play(context, url, start)
                    if (history?.episodeUrl == sourceUrl) PlaybackSession.updateSpeed(history.speed)
                    playingUrl = url
                }
            }
        },
        playingUrl = playingUrl,
        onExpandPlayer = onEnterFullscreen,
        previewTitle = previewTitle,
        previewPoster = previewPoster,
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
