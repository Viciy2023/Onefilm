package com.onefilm.android.tv.compose.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.TvPosterCard

@Composable
fun LibraryScreen(
    data: LibraryData?,
    onCardClick: (MediaCard) -> Unit,
    initialTab: Int = 0,
) {
    var selected by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text("我的片库", style = MaterialTheme.typography.headlineMedium)
        TabRow(selectedTabIndex = selected) {
            listOf("收藏", "历史").forEachIndexed { index, title ->
                Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) })
            }
        }
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val items = if (selected == 0) data.favorites else data.history
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (selected == 0) "暂无收藏内容" else "暂无观看记录", style = MaterialTheme.typography.bodyLarge)
                    }
                } else LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    itemsIndexed(items, key = { index, card -> "${card.key}:${card.id}:$index" }) { _, card ->
                        TvPosterCard(
                            title = card.title,
                            posterUrl = card.posterUrl,
                            subtitle = card.subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onCardClick(card) },
                        )
                    }
                }
            }
        }
    }
}
