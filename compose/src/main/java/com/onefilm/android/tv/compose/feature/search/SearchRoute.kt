package com.onefilm.android.tv.compose.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchMetaDataSource
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(
    dataSource: SearchDataSource,
    metaDataSource: SearchMetaDataSource,
    onCardClick: (MediaCard) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(dataSource) } },
    )
    val state by viewModel.state.collectAsState()
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var hot by remember { mutableStateOf(metaDataSource.hot()) }
    LaunchedEffect(Unit) { hot = metaDataSource.refreshHot() }
    val scope = rememberCoroutineScope()
    SearchScreen(
        state = state,
        onSearch = viewModel::search,
        onRetry = viewModel::retry,
        onCardClick = onCardClick,
        history = remember { metaDataSource.history() },
        hot = hot,
        suggestions = suggestions,
        onQueryChange = { q -> scope.launch { suggestions = metaDataSource.suggest(q) } },
    )
}
