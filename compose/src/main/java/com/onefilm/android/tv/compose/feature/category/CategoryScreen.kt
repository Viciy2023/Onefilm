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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.CategoryFilterGroup
import com.onefilm.android.tv.compose.bridge.CategoryPage
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.PosterStyleController
import com.onefilm.android.tv.compose.design.component.PosterCard
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource

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
            com.onefilm.android.tv.compose.design.component.AppLoadingState()
        }
        is CategoryUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.message.ifBlank { stringResource(R.string.category_load_failed) }, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onRetry) { Text(stringResource(R.string.detail_retry)) }
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
    val firstFilterFocus = remember { FocusRequester() }
    LaunchedEffect(showFilters) {
        delay(120)
        if (showFilters) {
            runCatching { firstFilterFocus.requestFocus() }
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (PosterStyleController.horizontal) 5 else 6),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = page.items,
                key = { index, card -> "${card.key}:${card.id}:$index" },
            ) { _, card ->
                PosterCard(
                    title = card.title,
                    posterUrl = card.posterUrl,
                    landscapeUrl = card.landscapeUrl,
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

        if (showFilters && page.filters.isNotEmpty()) {
            // 筛选面板放在独立窗口（Dialog）里，返回键由面板窗口自己消费，
            // 关闭后活动窗口的焦点仍是打开面板前的分类标签，不会被带回主页。
            Dialog(
                onDismissRequest = onDismissFilters,
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnClickOutside = false,
                    dismissOnBackPress = true,
                ),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { event ->
                            val native = event.nativeKeyEvent
                            if (native.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                                if (native.action == android.view.KeyEvent.ACTION_UP) onDismissFilters()
                                true
                            } else {
                                false
                            }
                        },
                ) {
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
                        onDismiss = onDismissFilters,
                        firstOptionFocus = firstFilterFocus,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBottomSheet(
    filters: List<CategoryFilterGroup>,
    selectedFilters: Map<String, String>,
    onToggleFilter: (filterKey: String, value: String, selected: Boolean) -> Unit,
    onDismiss: () -> Unit,
    firstOptionFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    BackHandler { onDismiss() }
    val firstGroupIndex = filters.indexOfFirst { it.options.isNotEmpty() }
    Column(
        modifier
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        filters.forEachIndexed { groupIndex, group ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = group.name,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(72.dp),
                )
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(end = 4.dp),
                ) {
                    items(group.options.size) { optionIndex ->
                        val option = group.options[optionIndex]
                        val checked = selectedFilters[group.key] == option.value
                        FilterOption(
                            text = option.name.ifBlank { option.value },
                            selected = checked,
                            focusRequester = if (groupIndex == firstGroupIndex && optionIndex == 0) {
                                firstOptionFocus
                            } else null,
                            onClick = { onToggleFilter(group.key, option.value, !checked) },
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        color = when {
            selected -> MaterialTheme.colorScheme.onPrimary
            focused -> MaterialTheme.colorScheme.onSurface
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
        },
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier,
            )
            .clip(shape)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primary
                    focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                },
            )
            .then(
                if (focused) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}
