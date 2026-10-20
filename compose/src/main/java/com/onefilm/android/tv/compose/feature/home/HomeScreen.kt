package com.onefilm.android.tv.compose.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.HeroBanner
import com.onefilm.android.tv.compose.design.component.PosterItem
import com.onefilm.android.tv.compose.design.component.TvPosterCard
import com.onefilm.android.tv.compose.design.component.SectionRow

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit = {},
    isTv: Boolean = false,
) {
    when (state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.onefilm.android.tv.compose.design.component.AppLoadingState()
        }
        HomeUiState.Empty -> StatusMessage("暂无内容", onRetry)
        is HomeUiState.Error -> StatusMessage(state.message, onRetry)
        is HomeUiState.Content ->
            if (isTv) TvHomeContent(state, onCardClick) else HomeContent(state, onCardClick, onCategoryClick)
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState.Content,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit,
) {
    val feed = state.feed
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        feed.hero.firstOrNull()?.let { hero ->
            item(key = "hero") {
                HeroBanner(
                    title = hero.title,
                    description = hero.subtitle,
                    backdropUrl = hero.posterUrl,
                    onPlay = { onCardClick(hero) },
                    onDetails = { onCardClick(hero) },
                )
            }
        }
        if (feed.categories.isNotEmpty()) {
            item(key = "categories") {
                CategoryRail(feed.categories, onCategoryClick)
            }
        }
        feed.sections.forEachIndexed { index, section ->
            item(key = "section:$index:${section.title}") {
                SectionRow(
                    title = section.title,
                    items = section.items.map { it.toPosterItem() },
                    onItemClick = { item ->
                        onCardClick(
                            MediaCard(
                                key = item.key,
                                id = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                posterUrl = item.posterUrl,
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun MediaCard.toPosterItem() = PosterItem(
    key = key,
    id = id,
    title = title,
    posterUrl = posterUrl,
    subtitle = subtitle,
)

@Composable
private fun TvHomeContent(
    state: HomeUiState.Content,
    onCardClick: (MediaCard) -> Unit,
) {
    val section = state.feed.sections.firstOrNull { it.items.isNotEmpty() } ?: return
    TvPosterSection(section.items, onCardClick)
}

@Composable
private fun TvPosterSection(
    items: List<MediaCard>,
    onCardClick: (MediaCard) -> Unit,
) {
    val pages = items.chunked(10)
    val listState = rememberLazyListState()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val horizontalPadding = 48.dp
        val gap = 16.dp
        // 5 列必须铺满屏宽；高度不足时允许纵向滚动，而不是把卡片缩到屏幕中间。
        val cardWidth = ((maxWidth - horizontalPadding * 2 - gap * 4) / 5).coerceAtLeast(120.dp)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                top = 36.dp,
                end = horizontalPadding,
                bottom = 8.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            items(pages.size) { pageIndex ->
                Column(
                    Modifier
                        .fillParentMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    val page = pages[pageIndex]
                    for (row in 0 until 2) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            for (col in 0 until 5) {
                                val item = page.getOrNull(row * 5 + col)
                                if (item != null) {
                                    TvPosterCard(
                                        title = item.title,
                                        posterUrl = item.posterUrl,
                                        subtitle = item.subtitle,
                                        width = cardWidth,
                                        onClick = { onCardClick(item) },
                                    )
                                } else {
                                    Spacer(Modifier.width(cardWidth))
                                }
                            }
                        }
                        if (row == 0) Spacer(Modifier.height(gap))
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
        if (pages.size > 1) {
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val current = listState.firstVisibleItemIndex
                repeat(pages.size) { index ->
                    Box(
                        Modifier
                            .width(if (index == current) 18.dp else 6.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index == current) MaterialTheme.colorScheme.onBackground
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRail(
    categories: List<HomeCategory>,
    onCategoryClick: (HomeCategory) -> Unit,
) {
    Column {
        Text(
            text = "分类",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            itemsIndexed(
                items = categories,
                key = { index, category -> "${category.key}:${category.id}:$index" },
            ) { _, category ->
                Text(
                    text = category.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onCategoryClick(category) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                )
            }
        }
    }
}

@Composable
private fun CenterMessage(message: String, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
            Spacer(Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun StatusMessage(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}
