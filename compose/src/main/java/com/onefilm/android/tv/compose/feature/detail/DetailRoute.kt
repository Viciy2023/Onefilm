package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.FavoriteStore

@Composable
fun DetailRoute(
    key: String,
    id: String,
    dataSource: DetailDataSource,
    favoriteStore: FavoriteStore,
    onBack: () -> Unit,
    onPlay: (episodeName: String?) -> Unit,
) {
    val viewModel: DetailViewModel = viewModel(
        key = "$key:$id",
        factory = viewModelFactory {
            initializer { DetailViewModel(key, id, dataSource) }
        },
    )
    val state by viewModel.state.collectAsState()
    var favorite by remember(key, id) { mutableStateOf(favoriteStore.isFavorite(key, id)) }
    LaunchedEffect(key, id) { viewModel.retry() }
    DetailScreen(
        state = state,
        onRetry = viewModel::retry,
        onBack = onBack,
        onPlay = onPlay,
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
