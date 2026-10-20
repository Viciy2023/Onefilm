@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.onefilm.android.tv.compose.feature.category

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.CategoryFilterGroup
import com.onefilm.android.tv.compose.bridge.CategoryPage
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.TvPosterCard

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
    LaunchedEffect(Unit) {
        CategoryFilterController.show.collect { showFilters = true }
    }
    BackHandler(enabled = showFilters) { showFilters = false }

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
            page = state.page,
            selectedFilters = selectedFilters,
            showFilters = showFilters,
            onDismissFilters = { showFilters = false },
            onCardClick = onCardClick,
            onToggleFilter = onToggleFilter,
            onLoadMore = onLoadMore,
            endReached = state.endReached,
        )
    }
}

@Composable
private fun CategoryContent(
    page: CategoryPage,
    selectedFilters: Map<String, String>,
    showFilters: Boolean,
    onDismissFilters: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onToggleFilter: (filterKey: String, value: String, selected: Boolean) -> Unit,
    onLoadMore: () -> Unit,
    endReached: Boolean,
) {
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            itemsIndexed(
                items = page.items,
                key = { index, card -> "${card.key}:${card.id}:$index" },
            ) { _, card ->
                TvPosterCard(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    subtitle = card.subtitle,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onCardClick(card) },
                )
            }
            if (!endReached) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LaunchedEffect(page.items.size) { onLoadMore() }
                    Box(
                        Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
        }

        if (showFilters) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissFilters,
                    ),
            )
            FilterBottomSheet(
                filters = page.filters,
                selectedFilters = selectedFilters,
                onToggleFilter = onToggleFilter,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterBottomSheet(
    filters: List<CategoryFilterGroup>,
    selectedFilters: Map<String, String>,
    onToggleFilter: (filterKey: String, value: String, selected: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 30.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        filters.forEach { group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name + "：",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp),
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 8.dp),
                ) {
                    items(group.options) { option ->
                        val checked = selectedFilters[group.key] == option.value
                        FilterOption(
                            text = option.name.ifBlank { option.value },
                            selected = checked,
                            onClick = { onToggleFilter(group.key, option.value, !checked) },
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun FilterOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Text(
        text = text,
        color = when {
            selected -> MaterialTheme.colorScheme.background
            else -> MaterialTheme.colorScheme.onSurface
        },
        fontSize = 18.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                RoundedCornerShape(percent = 50),
            )
            .clip(RoundedCornerShape(percent = 50))
            .background(
                if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}
