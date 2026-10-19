package com.onefilm.android.tv.compose.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.HeroBanner
import com.onefilm.android.tv.compose.design.component.PosterItem
import com.onefilm.android.tv.compose.design.component.SectionRow

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit = {},
) {
    when (state) {
        HomeUiState.Loading -> CenterMessage("正在加载") { CircularProgressIndicator() }
        HomeUiState.Empty -> StatusMessage("暂无内容", onRetry)
        is HomeUiState.Error -> StatusMessage(state.message, onRetry)
        is HomeUiState.Content -> HomeContent(state, onCardClick, onCategoryClick)
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
