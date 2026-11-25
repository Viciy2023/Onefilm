package com.onefilm.android.tv.compose.feature.category

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard

/** 目录层级：folder=true 的站点可以一层层往下点。 */
private data class CategoryLevel(val tid: String, val title: String)

@Composable
fun CategoryRoute(
    key: String,
    tid: String,
    title: String,
    dataSource: CategoryDataSource,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    var stack by remember(key, tid) { mutableStateOf(listOf(CategoryLevel(tid, title))) }
    val current = stack.last()
    val viewModel: CategoryViewModel = viewModel(
        key = "$key:${current.tid}",
        factory = viewModelFactory {
            initializer { CategoryViewModel(key, current.tid, dataSource) }
        },
    )
    val state by viewModel.state.collectAsState()
    val selectedFilters by viewModel.selectedFilters.collectAsState()
    DisposableEffect(key, current.tid) {
        CategoryFilterController.bind(key, current.tid)
        onDispose { CategoryFilterController.unbind(key, current.tid) }
    }
    LaunchedEffect(key, current.tid) { viewModel.ensureLoaded() }
    // 进入子目录后，系统返回键先退回上一级，而不是直接离开分类页。
    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }
    CategoryScreen(
        title = current.title,
        state = state,
        selectedFilters = selectedFilters,
        onRetry = viewModel::retry,
        onBack = {
            if (stack.size > 1) stack = stack.dropLast(1) else onBack()
        },
        onCardClick = { card ->
            // 目录卡片：用它的 id 作为下一级的分类 id，与旧 FolderFragment 一致。
            if (card.folder) stack = stack + CategoryLevel(card.id, card.title) else onCardClick(card)
        },
        onToggleFilter = viewModel::toggleFilter,
        onLoadMore = viewModel::loadMore,
    )
}
