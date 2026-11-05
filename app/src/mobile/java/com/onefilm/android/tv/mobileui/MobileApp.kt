package com.onefilm.android.tv.mobileui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.feature.home.HomeUiState
import com.onefilm.android.tv.compose.feature.search.SearchSession
import com.onefilm.android.tv.compose.feature.search.SearchUiState
import com.onefilm.android.tv.mobileui.component.MobileHorizontalGrid
import com.onefilm.android.tv.mobileui.component.MobileRemoteImage
import com.onefilm.android.tv.ui.activity.LiveActivity
import com.onefilm.android.tv.ui.activity.VideoActivity
import com.onefilm.android.tv.R
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MobileTab(val labelRes: Int) {
    HOME(R.string.mobile_tab_home),
    SEARCH(R.string.menu_search),
    LIBRARY(R.string.mobile_tab_library),
    LIVE(R.string.nav_live),
    SETTINGS(R.string.nav_setting),
}

@Composable
fun MobileApp() {
    val context = LocalContext.current
    var homeState by remember {
        mutableStateOf<HomeUiState>(
            HomeFeedCache.feed?.let { HomeUiState.Content(it) } ?: HomeUiState.Loading,
        )
    }
    var homeRefresh by remember { mutableIntStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(MobileTab.HOME) }
    var libraryTab by rememberSaveable { mutableIntStateOf(0) }
    var categoryTarget by remember { mutableStateOf<HomeCategory?>(null) }
    var searchResetToken by remember { mutableIntStateOf(0) }

    fun resetSearch() {
        SearchSession.cancel()
        searchResetToken++
    }

    fun playCard(card: MediaCard) {
        val activity = context as? Activity ?: return
        if (card.key.isNotBlank() && card.id.isNotBlank()) {
            VideoActivity.start(activity, card.key, card.id, card.title, card.posterUrl)
        }
    }

    fun openCard(card: MediaCard) {
        playCard(card)
    }

    fun openLive() {
        val activity = context as? Activity ?: return
        // Start immediately so the click is never blocked by a slow/unreachable M3U
        // config; LiveActivity loads the saved live URL itself.
        LiveActivity.start(activity)
    }

    LaunchedEffect(homeRefresh) {
        homeState = runCatching { ComposeDependencies.homeDataSource.load() }
            .fold(
                onSuccess = { feed ->
                    if (feed.hero.isEmpty() && feed.sections.isEmpty()) HomeUiState.Empty
                    else HomeUiState.Content(feed)
                },
                onFailure = { HomeUiState.Error() },
            )
    }

    LaunchedEffect(Unit) {
        // Live is a direct action, not a retained Compose destination. Avoid restoring
        // the old portrait placeholder after a process/config recreation.
        if (tab == MobileTab.LIVE) tab = MobileTab.HOME
    }

    BackHandler(enabled = categoryTarget != null) { categoryTarget = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MobileTopBar(
            title = categoryTarget?.name
                ?: if (tab == MobileTab.HOME) stringResource(R.string.mobile_app_brand)
                else stringResource(tab.labelRes),
        )
        Box(Modifier.weight(1f)) {
            val category = categoryTarget
            if (category != null) {
                MobileCategoryScreen(
                    category = category,
                    dataSource = ComposeDependencies.categoryDataSource,
                    onBack = { categoryTarget = null },
                    onCardClick = ::openCard,
                )
            } else {
                when (tab) {
                    MobileTab.HOME -> MobileHomeScreen(
                        state = homeState,
                        onRetry = { homeRefresh++ },
                        onCardClick = ::openCard,
                        onCategoryClick = { categoryTarget = it },
                    )
                    MobileTab.SEARCH -> MobileSearchScreen(
                        dataSource = ComposeDependencies.searchDataSource,
                        resetToken = searchResetToken,
                        onCardClick = ::openCard,
                    )
                    MobileTab.LIBRARY -> MobileLibraryScreen(
                        dataSource = ComposeDependencies.libraryDataSource,
                        initialTab = libraryTab,
                        onTabChange = { libraryTab = it },
                        onCardClick = ::openCard,
                    )
                    MobileTab.LIVE -> MobileLiveScreen(onOpen = ::openLive)
                    MobileTab.SETTINGS -> MobileSettingsScreen(
                        dataSource = ComposeDependencies.settingsDataSource,
                        onConfigChanged = { homeRefresh++ },
                    )
                }
            }
        }
        MobileBottomBar(
            selected = tab,
            onSelect = { selected ->
                val wasSearch = tab == MobileTab.SEARCH
                if (selected == MobileTab.LIVE) {
                    if (wasSearch) resetSearch()
                    openLive()
                } else {
                    if (wasSearch && selected != MobileTab.SEARCH) resetSearch()
                    categoryTarget = null
                    tab = selected
                }
            },
        )
    }
}

@Composable
private fun MobileTopBar(title: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "ONEFILM",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MobileBottomBar(selected: MobileTab, onSelect: (MobileTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileTab.entries.forEach { tab ->
            Text(
                text = stringResource(tab.labelRes),
                color = if (tab == selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                fontWeight = if (tab == selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (tab == selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
private fun MobileHomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit,
) {
    when (state) {
        HomeUiState.Loading -> MobileLoading()
        HomeUiState.Empty -> MobileStatus(stringResource(R.string.mobile_home_empty), onRetry)
        is HomeUiState.Error -> MobileStatus(state.message, onRetry)
        is HomeUiState.Content -> MobileHomeContent(state, onCardClick, onCategoryClick)
    }
}

@Composable
private fun MobileHomeContent(
    state: HomeUiState.Content,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit,
) {
    val feed = state.feed
    val recommendationIndex = feed.sections.indexOfFirst {
        it.title == "推荐" && it.items.isNotEmpty()
    }
    val recommendation = feed.sections.getOrNull(recommendationIndex)?.items
        ?: feed.hero.takeIf { it.isNotEmpty() }
    // 首页只保留「推荐」和「继续观看」；收藏交给底部导航，不再占用首页。
    val continueWatching = feed.sections.firstOrNull {
        it.title == "继续观看" && it.items.isNotEmpty()
    }
    val hasRecommend = !recommendation.isNullOrEmpty()
    val continueItems = continueWatching?.items.orEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPad = 16.dp
        val vPad = 8.dp
        val rowGap = 8.dp
        val sectionGap = 12.dp
        val titleGap = 6.dp
        val titleHeight = 24.dp
        val chipsHeight = if (feed.categories.isNotEmpty()) 46.dp else 0.dp
        val sectionCount = (if (hasRecommend) 1 else 0) + 1
        val childCount = (if (feed.categories.isNotEmpty()) 1 else 0) + sectionCount
        // 把标题、分区间距、行间距都算进固定高度，避免最后一行被裁掉。
        val fixed = chipsHeight + titleHeight * sectionCount + titleGap * sectionCount +
            sectionGap * (childCount - 1) + rowGap * 2 + vPad * 2
        val totalRows = (if (hasRecommend) 3 else 0) + 1
        val rowHeight = ((maxHeight - fixed) / totalRows).coerceAtLeast(96.dp)
        val cardWidth = (rowHeight * 2f / 3f).coerceAtLeast(64.dp)

        Column(
            Modifier.fillMaxSize().padding(horizontal = hPad, vertical = vPad),
            verticalArrangement = Arrangement.spacedBy(sectionGap),
        ) {
            if (feed.categories.isNotEmpty()) {
                LazyRow(
                    Modifier.height(chipsHeight - 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(feed.categories) { category ->
                        Text(
                            text = category.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .clickable { onCategoryClick(category) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            if (hasRecommend) {
                MobileHomeSection(
                    title = stringResource(R.string.mobile_home_recommend),
                    items = recommendation!!,
                    rows = 3,
                    rowHeight = rowHeight,
                    cardWidth = cardWidth,
                    rowGap = rowGap,
                    onCardClick = onCardClick,
                )
            }
            MobileHomeSection(
                title = stringResource(R.string.mobile_home_continue),
                items = continueItems,
                rows = 1,
                rowHeight = rowHeight,
                cardWidth = cardWidth,
                rowGap = rowGap,
                onCardClick = onCardClick,
                placeholder = if (continueItems.isEmpty()) stringResource(R.string.mobile_home_continue_empty) else null,
            )
        }
    }
}

/**
 * 首页分区：固定行数的横向网格（非分页），左右连续滑动。
 * 每列是一个纵向 strip，避免出现「一屏一页」的跳转感。
 */
@Composable
private fun MobileHomeSection(
    title: String,
    items: List<MediaCard>,
    rows: Int,
    rowHeight: Dp,
    cardWidth: Dp,
    rowGap: Dp,
    onCardClick: (MediaCard) -> Unit,
    placeholder: String? = null,
) {
    if (items.isEmpty() && placeholder == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (items.isEmpty()) {
            MobileHomePlaceholderCard(cardWidth, rowHeight, placeholder.orEmpty())
        } else {
            MobileHorizontalGrid(
                items = items,
                rows = rows,
                rowHeight = rowHeight,
                cardWidth = cardWidth,
                rowGap = rowGap,
                onCardClick = onCardClick,
            )
        }
    }
}

/** 首页空态占位卡：显示 Logo 和提示文案，有真实数据后自动被替换。 */
@Composable
private fun MobileHomePlaceholderCard(width: Dp, height: Dp, label: String) {
    Column(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun MobileSearchScreen(
    dataSource: SearchDataSource,
    resetToken: Int,
    onCardClick: (MediaCard) -> Unit,
) {
    val context = LocalContext.current
    var query by rememberSaveable(resetToken) { mutableStateOf("") }
    var history by remember { mutableStateOf(MobileSearchHistory.load(context)) }
    val state by SearchSession.state.collectAsState()

    fun submit(keywordValue: String = query) {
        val keyword = keywordValue.trim()
        if (keyword.isEmpty()) return
        query = keyword
        history = MobileSearchHistory.add(context, keyword)
        SearchSession.start(dataSource, keyword)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f).height(44.dp),
                decorationBox = { innerTextField ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                stringResource(R.string.mobile_search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Button(
                onClick = { submit() },
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
            ) { Text(stringResource(R.string.menu_search)) }
        }

        if (history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(history) { keyword ->
                        Text(
                            text = keyword,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { submit(keyword) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
                TextButton(
                    onClick = {
                        MobileSearchHistory.clear(context)
                        history = emptyList()
                    },
                ) { Text(stringResource(R.string.mobile_search_clear), fontSize = 12.sp) }
            }
        }

        when (val current = state) {
            SearchUiState.Idle -> MobileHint(stringResource(R.string.mobile_search_idle))
            SearchUiState.Loading -> MobileLoading()
            is SearchUiState.Empty -> MobileHint(stringResource(R.string.mobile_search_empty, current.keyword))
            is SearchUiState.Error -> MobileHint(current.message)
            is SearchUiState.Content -> {
                Text(
                    stringResource(R.string.mobile_search_result_count, current.items.size) +
                        if (!current.done) " · " + stringResource(R.string.mobile_search_searching) else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // 4 行起步，屏幕更高时按可用高度增加行数（平板），列的宽度跟随行高。
                    val rowGap = 8.dp
                    val minRowHeight = 120.dp
                    val rows = ((maxHeight + rowGap) / (minRowHeight + rowGap))
                        .toInt()
                        .coerceAtLeast(4)
                    val rowHeight = ((maxHeight - rowGap * (rows - 1)) / rows)
                        .coerceAtLeast(96.dp)
                    val cardWidth = rowHeight * 2f / 3f
                    MobileHorizontalGrid(
                        items = current.items,
                        rows = rows,
                        rowHeight = rowHeight,
                        cardWidth = cardWidth,
                        rowGap = rowGap,
                        colGap = 10.dp,
                        showSite = true,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileLibraryScreen(
    dataSource: LibraryDataSource,
    initialTab: Int,
    onTabChange: (Int) -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    var data by remember { mutableStateOf<LibraryData?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { data = runCatching { dataSource.load() }.getOrNull() }
    val items = if (initialTab == 1) data?.history.orEmpty() else data?.favorites.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(stringResource(R.string.mobile_tab_library), stringResource(R.string.mobile_library_history)).forEachIndexed { index, label ->
                TextButton(onClick = { onTabChange(index) }) {
                    Text(
                        label,
                        fontWeight = if (initialTab == index) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        if (data == null) {
            MobileLoading()
        } else if (items.isEmpty()) {
            MobileHint(stringResource(R.string.mobile_library_empty))
        } else {
            Text(
                text = stringResource(R.string.mobile_library_swipe_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { "${it.key}|${it.id}" }) { card ->
                    MobileSwipeDeleteRow(
                        card = card,
                        onClick = { onCardClick(card) },
                        onDelete = {
                            if (initialTab == 1) dataSource.deleteHistory(card)
                            else dataSource.deleteFavorite(card)
                            data = runCatching { dataSource.load() }.getOrNull()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSwipeDeleteRow(
    card: MediaCard,
    onClick: () -> Unit,
    onDelete: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 80.dp.toPx() }
    val maxSwipe = with(density) { 150.dp.toPx() }
    var offsetX by remember(card.key, card.id) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.menu_delete),
            color = MaterialTheme.colorScheme.onError,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        MobileMediaRow(
            card = card,
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(card.key, card.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-maxSwipe, maxSwipe)
                        },
                        onDragEnd = {
                            if (abs(offsetX) >= threshold) {
                                scope.launch {
                                    offsetX = if (offsetX > 0f) maxSwipe else -maxSwipe
                                    onDelete()
                                }
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = { offsetX = 0f },
                    )
                },
        )
    }
}

@Composable
private fun MobileMediaRow(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileRemoteImage(
            rawUrl = card.posterUrl,
            contentDescription = card.title,
            modifier = Modifier
                .width(64.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = card.title.ifBlank { stringResource(R.string.mobile_unnamed) },
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val siteName = card.siteName
            if (!siteName.isNullOrBlank()) {
                Text(
                    siteName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val subtitle = card.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MobileLiveScreen(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.nav_live), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.mobile_live_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpen) { Text(stringResource(R.string.mobile_live_open)) }
    }
}

@Composable
private fun MobileLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MobileHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MobileStatus(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.mobile_retry)) }
    }
}
