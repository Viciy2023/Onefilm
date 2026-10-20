package com.onefilm.android.tv.compose.feature.category

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard

@Composable
fun CategoryRoute(
    key: String,
    tid: String,
    title: String,
    dataSource: CategoryDataSource,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    val viewModel: CategoryViewModel = viewModel(
        key = "$key:$tid",
        factory = viewModelFactory {
            initializer { CategoryViewModel(key, tid, dataSource) }
        },
    )
    val state by viewModel.state.collectAsState()
    val selectedFilters by viewModel.selectedFilters.collectAsState()
    DisposableEffect(key, tid) {
        CategoryFilterController.bind(key, tid)
        onDispose { CategoryFilterController.unbind(key, tid) }
    }
    LaunchedEffect(key, tid) { viewModel.retry() }
    CategoryScreen(
        title = title,
        state = state,
        selectedFilters = selectedFilters,
        onRetry = viewModel::retry,
        onBack = onBack,
        onCardClick = onCardClick,
        onToggleFilter = viewModel::toggleFilter,
        onLoadMore = viewModel::loadMore,
    )
}
