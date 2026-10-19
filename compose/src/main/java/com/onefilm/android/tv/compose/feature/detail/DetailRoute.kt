package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.FavoriteStore
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import kotlinx.coroutines.launch

@Composable
fun DetailRoute(
    key: String,
    id: String,
    dataSource: DetailDataSource,
    favoriteStore: FavoriteStore,
    playbackResolver: PlaybackResolver,
    onBack: () -> Unit,
    onPlayUrl: (url: String) -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "$key:$id",
        factory = viewModelFactory {
            initializer { DetailViewModel(key, id, dataSource) }
        },
    )
    val state by viewModel.state.collectAsState()
    var favorite by remember(key, id) { mutableStateOf(favoriteStore.isFavorite(key, id)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(key, id) { viewModel.retry() }
    DetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onPlay = { flagName, episodeUrl ->
            val info = (state as? DetailUiState.Content)?.info
            val flag = info?.flags?.firstOrNull { it.name == flagName } ?: info?.flags?.firstOrNull()
            val sourceUrl = episodeUrl?.takeIf { it.isNotBlank() }
                ?: flag?.episodes?.firstOrNull()?.url
            if (flag != null && !sourceUrl.isNullOrBlank()) {
                scope.launch {
                    val url = playbackResolver.resolve(key, flag.name, sourceUrl)
                    if (!url.isNullOrBlank()) onPlayUrl(url)
                }
            }
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
