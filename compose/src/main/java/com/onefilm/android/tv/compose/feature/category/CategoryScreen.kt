package com.onefilm.android.tv.compose.feature.category

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.CategoryPage
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.PosterCard

@Composable
fun CategoryScreen(
    title: String,
    state: CategoryUiState,
    selectedFilters: Map<String, String>,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onToggleFilter: (filterKey: String, value: String, selected: Boolean) -> Unit,
    onLoadMore: () -> Unit,
) {
    var showFilters by rememberSaveable { mutableStateOf(false) }
    when (state) {
        CategoryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is CategoryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text("重试") }
            }
        }
        is CategoryUiState.Content -> CategoryContent(
            title,
            state.page,
            selectedFilters,
            showFilters,
            onToggleShow = { showFilters = !showFilters },
            onBack,
            onCardClick,
            onToggleFilter,
            onLoadMore,
            state.endReached,
        )
    }
}

@Composable
private fun CategoryContent(
    fallbackTitle: String,
    page: CategoryPage,
    selectedFilters: Map<String, String>,
    showFilters: Boolean,
    onToggleShow: () -> Unit,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onToggleFilter: (filterKey: String, value: String, selected: Boolean) -> Unit,
    onLoadMore: () -> Unit,
    endReached: Boolean,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹ ${page.title.ifBlank { fallbackTitle }}",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBack),
            )
            if (page.filters.isNotEmpty()) {
                Text(
                    text = if (showFilters) "收起筛选" else "筛选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onToggleShow)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        if (showFilters) {
            page.filters.forEach { group ->
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(group.options) { option ->
                        val checked = selectedFilters[group.key] == option.value
                        Text(
                            text = option.name.ifBlank { option.value },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                                .clickable {
                                    onToggleFilter(group.key, option.value, !checked)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            itemsIndexed(
                items = page.items,
                key = { index, card -> "${card.key}:${card.id}:$index" },
            ) { _, card ->
                PosterCard(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    subtitle = card.subtitle,
                    onClick = { onCardClick(card) },
                )
            }
            if (!endReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LaunchedEffect(page.items.size) { onLoadMore() }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}
