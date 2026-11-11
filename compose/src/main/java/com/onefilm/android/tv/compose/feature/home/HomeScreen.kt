package com.onefilm.android.tv.compose.feature.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeSection
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.design.component.HeroBanner
import com.onefilm.android.tv.compose.design.component.PosterItem
import com.onefilm.android.tv.compose.design.PosterStyleController
import com.onefilm.android.tv.compose.design.component.PosterCard
import com.onefilm.android.tv.compose.design.component.SectionRow
import com.onefilm.android.tv.compose.design.component.TvHeroRow
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.stringResource

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit = {},
    onOpenConfig: () -> Unit = {},
    firstActionFocus: FocusRequester? = null,
    isTv: Boolean = false,
) {
    when (state) {
        HomeUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            com.onefilm.android.tv.compose.design.component.AppLoadingState()
        }
        HomeUiState.Empty -> TvEmptyState(
            onOpenConfig = onOpenConfig,
            onRetry = onRetry,
            firstActionFocus = firstActionFocus,
        )
        is HomeUiState.Error -> StatusMessage(state.message.ifBlank { stringResource(R.string.home_load_failed) }, onRetry)
        is HomeUiState.Content ->
            if (isTv) TvHomeContent(state, onCardClick) else HomeContent(state, onCardClick, onCategoryClick)
    }
}

/**
 * 未配置接口时的引导页：直接铺在主页面背景上，不使用弹窗/卡片容器。
 *
 * - LOGO 作为整页背景水印（30% 透明度）；
 * - 标题 40sp、正文 20sp/34sp 行高，适配电视观看距离；
 * - 两个动作复用「接口分类标签」样式（18dp 圆角 + surface 底色 + 扁高度），
 *   焦点态叠加 2dp 主色描边与缩放，保证遥控器可见性。
 */
@Composable
private fun TvEmptyState(
    onOpenConfig: () -> Unit,
    onRetry: () -> Unit,
    firstActionFocus: FocusRequester? = null,
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .alpha(0.12f),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "ONEFILM",
                style = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 6.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.home_welcome_title),
                fontSize = 40.sp,
                lineHeight = 52.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.home_welcome_body),
                fontSize = 20.sp,
                lineHeight = 34.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                EmptyTagAction(
                    label = stringResource(R.string.home_action_config),
                    onClick = onOpenConfig,
                    focusRequester = firstActionFocus,
                )
                EmptyTagAction(
                    label = stringResource(R.string.home_action_retry),
                    onClick = onRetry,
                )
            }
        }
    }
}

/** 与「接口分类标签」保持一致的扁按钮。 */
@Composable
private fun EmptyTagAction(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(18.dp)
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "emptyActionScale",
    )
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .scale(scale)
            .clip(shape)
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surface,
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = 26.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
        for ((index, section) in feed.sections.withIndex()) {
            item(key = "section:$index:${section.key ?: section.title}") {
                val sectionTitle = section.displayTitle()
                SectionRow(
                    title = sectionTitle,
                    items = section.items.map { it.toPosterItem() },
                    onItemClick = { item ->
                        onCardClick(
                            MediaCard(
                                key = item.key,
                                id = item.id,
                                title = item.title,
                                subtitle = item.subtitle,
                                posterUrl = item.posterUrl,
                                landscapeUrl = item.landscapeUrl,
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
    landscapeUrl = landscapeUrl,
)

@Composable
private fun TvHomeContent(
    state: HomeUiState.Content,
    onCardClick: (MediaCard) -> Unit,
) {
    val section = state.feed.sections.firstOrNull { it.key == "recommend" && it.items.isNotEmpty() }
        ?: state.feed.sections.firstOrNull { it.items.isNotEmpty() }
        ?: return
    val horizontal = PosterStyleController.horizontal
    // 横版模式：首行两张英雄卡（优先取 hero，其次取推荐段前两条），并避免在下方网格重复出现。
    val heroes = if (horizontal) {
        (state.feed.hero.ifEmpty { section.items })
            .distinctBy { "${it.key}@${it.id}" }
            .take(1)
    } else {
        emptyList()
    }
    val heroIds = heroes.map { "${it.key}@${it.id}" }.toSet()
    val rest = section.items.filterNot { "${it.key}@${it.id}" in heroIds }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (horizontal) 4 else 6),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (horizontal) 12.dp else 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (heroes.isNotEmpty()) {
            item(key = "home-hero", span = { GridItemSpan(maxLineSpan) }) {
                TvHeroRow(items = heroes, onCardClick = onCardClick)
            }
        }
        gridItemsIndexed(
            items = rest,
            key = { index, card -> "${card.key}:${card.id}:$index" },
        ) { _, item ->
            PosterCard(
                title = item.title,
                posterUrl = item.posterUrl,
                subtitle = item.subtitle,
                landscapeUrl = item.landscapeUrl,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onCardClick(item) },
            )
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
            text = stringResource(R.string.home_category),
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
                    text = category.name.ifBlank { stringResource(R.string.detail_unnamed) },
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
            Button(onClick = onRetry) { Text(stringResource(R.string.detail_retry)) }
        }
    }
}

/** 首页分区标题：合成分区按稳定 key 取本地化文案，接口分区沿用后端标题。 */
@Composable
private fun HomeSection.displayTitle(): String = when (key) {
    "history" -> stringResource(R.string.home_section_history)
    "favorites" -> stringResource(R.string.home_section_favorites)
    "recommend" -> stringResource(R.string.home_section_recommend)
    else -> title
}
