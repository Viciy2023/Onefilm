package com.onefilm.android.tv.compose.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource

@Composable
fun SearchRoute(
    dataSource: SearchDataSource,
    onCardClick: (MediaCard) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(dataSource) } },
    )
    val state by viewModel.state.collectAsState()
    SearchScreen(
        state = state,
        onSearch = viewModel::search,
        onRetry = viewModel::retry,
        onCardClick = onCardClick,
    )
}
