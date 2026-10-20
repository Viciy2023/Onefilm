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
import com.onefilm.android.tv.compose.bridge.SearchRank
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchRoute(
    dataSource: SearchDataSource,
    metaDataSource: SearchMetaDataSource,
    onCardClick: (MediaCard) -> Unit,
    initialQuery: String? = null,
) {
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory { initializer { SearchViewModel(dataSource) } },
    )
    val state by viewModel.state.collectAsState()
    var suggestions by remember { mutableStateOf(emptyList<String>()) }
    var hot by remember { mutableStateOf(metaDataSource.hot()) }
    var history by remember { mutableStateOf(metaDataSource.history()) }
    var rankings by remember { mutableStateOf<List<SearchRank>>(emptyList()) }
    var suggestJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(Unit) {
        hot = metaDataSource.refreshHot()
        rankings = metaDataSource.refreshRankings()
    }
    LaunchedEffect(initialQuery) {
        val keyword = initialQuery?.trim().orEmpty()
        if (keyword.isNotEmpty() && !viewModel.hasSession(keyword)) {
            metaDataSource.record(keyword)
            viewModel.search(keyword)
        }
    }
    val scope = rememberCoroutineScope()
    SearchScreen(
        state = state,
        onSearch = { keyword ->
            metaDataSource.record(keyword)
            history = metaDataSource.history()
            viewModel.search(keyword)
        },
        onRetry = viewModel::retry,
        onCardClick = onCardClick,
        history = history,
        hot = hot,
        rankings = rankings,
        suggestions = suggestions,
        initialQuery = initialQuery,
        onQueryChange = { q ->
            suggestJob?.cancel()
            if (q.isBlank()) {
                suggestions = emptyList()
            } else {
                suggestJob = scope.launch {
                    delay(250)
                    suggestions = metaDataSource.suggest(q)
                }
            }
        },
        onClearHistory = {
            metaDataSource.clearHistory()
            history = emptyList()
        },
    )
}
