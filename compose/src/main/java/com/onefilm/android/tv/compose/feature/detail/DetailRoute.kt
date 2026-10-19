package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.DetailDataSource

@Composable
fun DetailRoute(
    key: String,
    id: String,
    dataSource: DetailDataSource,
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
    LaunchedEffect(key, id) { viewModel.retry() }
    DetailScreen(state = state, onRetry = viewModel::retry, onBack = onBack, onPlay = onPlay)
}
