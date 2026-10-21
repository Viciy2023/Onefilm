package com.onefilm.android.tv.compose.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import kotlinx.coroutines.launch

@Composable
fun LibraryRoute(
    dataSource: LibraryDataSource,
    onCardClick: (MediaCard) -> Unit,
    initialTab: Int = 0,
) {
    var data by remember { mutableStateOf<LibraryData?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        data = runCatching { dataSource.load() }.getOrNull() ?: LibraryData(emptyList(), emptyList())
    }
    LaunchedEffect(Unit) { refresh() }
    LibraryScreen(
        data = data,
        onCardClick = onCardClick,
        initialTab = initialTab,
        onDeleteFavorite = { card -> scope.launch { dataSource.deleteFavorite(card); refresh() } },
        onClearFavorites = { scope.launch { dataSource.clearFavorites(); refresh() } },
        onDeleteHistory = { card -> scope.launch { dataSource.deleteHistory(card); refresh() } },
        onClearHistory = { scope.launch { dataSource.clearHistory(); refresh() } },
    )
}
