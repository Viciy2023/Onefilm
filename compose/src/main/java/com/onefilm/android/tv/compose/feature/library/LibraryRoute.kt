package com.onefilm.android.tv.compose.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard

@Composable
fun LibraryRoute(
    dataSource: LibraryDataSource,
    onCardClick: (MediaCard) -> Unit,
) {
    var data by remember { mutableStateOf<LibraryData?>(null) }
    LaunchedEffect(Unit) { data = runCatching { dataSource.load() }.getOrNull() ?: LibraryData(emptyList(), emptyList()) }
    LibraryScreen(data = data, onCardClick = onCardClick)
}
