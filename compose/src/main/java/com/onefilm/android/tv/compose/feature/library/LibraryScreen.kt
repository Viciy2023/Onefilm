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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.AppLogoLoading
import com.onefilm.android.tv.compose.design.component.TvPosterCard

@Composable
fun LibraryScreen(
    data: LibraryData?,
    onCardClick: (MediaCard) -> Unit,
    initialTab: Int = 0,
) {
    val historyMode = initialTab == 1
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text(
            text = if (historyMode) "历史" else "收藏",
            fontSize = 30.sp,
            style = MaterialTheme.typography.headlineMedium,
        )
        when {
            data == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogoLoading(size = 84.dp)
                    Text("哎呀！正在加载中呢，你等等呀！", fontSize = 18.sp)
                }
            }
            else -> {
                val items = if (historyMode) data.history else data.favorites
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (historyMode) "暂无观看记录" else "暂无收藏内容",
                            style = MaterialTheme.typography.bodyLarge,
                            fontSize = 18.sp,
                        )
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
