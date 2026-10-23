package com.onefilm.android.tv.mobileui

import android.app.Activity
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.feature.home.HomeUiState
import com.onefilm.android.tv.compose.feature.search.SearchSession
import com.onefilm.android.tv.compose.feature.search.SearchUiState
import com.onefilm.android.tv.mobileui.component.MobileRemoteImage
import com.onefilm.android.tv.ui.activity.LiveActivity
import com.onefilm.android.tv.ui.activity.VideoActivity

private enum class MobileTab(val label: String) {
    HOME("首页"),
    SEARCH("搜索"),
    LIBRARY("收藏"),
    LIVE("直播"),
    SETTINGS("设置"),
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

    fun openCard(card: MediaCard) {
        val activity = context as? Activity ?: return
        if (card.key.isNotBlank() && card.id.isNotBlank()) {
            VideoActivity.start(activity, card.key, card.id, card.title, card.posterUrl)
        }
    }

    fun openLive() {
        val activity = context as? Activity ?: return
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MobileTopBar(title = if (tab == MobileTab.HOME) "壹影" else tab.label)
        Box(Modifier.weight(1f)) {
            when (tab) {
                MobileTab.HOME -> MobileHomeScreen(
                    state = homeState,
                    onRetry = { homeRefresh++ },
                    onCardClick = ::openCard,
                )
                MobileTab.SEARCH -> MobileSearchScreen(
                    dataSource = ComposeDependencies.searchDataSource,
                    onCardClick = ::openCard,
                )
                MobileTab.LIBRARY -> MobileLibraryScreen(
                    dataSource = ComposeDependencies.libraryDataSource,
                    initialTab = libraryTab,
                    onTabChange = { libraryTab = it },
                    onCardClick = ::openCard,
                )
                MobileTab.LIVE -> MobileLiveScreen(
                    onOpen = ::openLive,
                )
                MobileTab.SETTINGS -> MobileSettingsScreen(
                    dataSource = ComposeDependencies.settingsDataSource,
                )
            }
        }
        MobileBottomBar(
            selected = tab,
            onSelect = { selected ->
                tab = selected
                if (selected == MobileTab.LIVE) {
                    openLive()
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
                text = tab.label,
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
) {
    when (state) {
        HomeUiState.Loading -> MobileLoading()
        HomeUiState.Empty -> MobileStatus("暂无内容", onRetry)
        is HomeUiState.Error -> MobileStatus(state.message, onRetry)
        is HomeUiState.Content -> MobileHomeContent(state, onCardClick)
    }
}

@Composable
private fun MobileHomeContent(state: HomeUiState.Content, onCardClick: (MediaCard) -> Unit) {
    val feed = state.feed
    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (feed.categories.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(feed.categories) { category ->
                        Text(
                            text = category.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        if (feed.hero.isNotEmpty()) {
            item { MobilePosterRail("推荐", feed.hero, onCardClick) }
        }
        items(feed.sections) { section ->
            MobilePosterRail(section.title, section.items, onCardClick)
        }
    }
}

@Composable
private fun MobilePosterRail(
    title: String,
    items: List<MediaCard>,
    onCardClick: (MediaCard) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { card ->
                MobilePosterCard(card) { onCardClick(card) }
            }
        }
    }
}

@Composable
private fun MobilePosterCard(card: MediaCard, onClick: () -> Unit) {
    Column(
        Modifier
            .width(136.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MobileRemoteImage(
            rawUrl = card.posterUrl,
            contentDescription = card.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = card.title.ifBlank { "未命名" },
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        val subtitle = card.subtitle
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MobileSearchScreen(
    dataSource: SearchDataSource,
    onCardClick: (MediaCard) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val state by SearchSession.state.collectAsState()

    fun submit() {
        val keyword = query.trim()
        if (keyword.isNotEmpty()) SearchSession.start(dataSource, keyword)
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索电影、剧集、演员") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { submit() }) { Text("搜索") }
        }
        when (val current = state) {
            SearchUiState.Idle -> MobileHint("输入关键词，开始聚合搜索")
            SearchUiState.Loading -> MobileLoading()
            is SearchUiState.Empty -> MobileHint("没有找到「${current.keyword}」")
            is SearchUiState.Error -> MobileHint(current.message)
            is SearchUiState.Content -> {
                Text(
                    "找到 ${current.items.size} 个结果" + if (!current.done) " · 搜索中" else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(current.items) { card ->
                        MobileMediaRow(card) { onCardClick(card) }
                    }
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
    LaunchedEffect(Unit) { data = runCatching { dataSource.load() }.getOrNull() }
    val items = if (initialTab == 1) data?.history.orEmpty() else data?.favorites.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("收藏", "历史").forEachIndexed { index, label ->
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
            MobileHint("这里还没有内容")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items) { card -> MobileMediaRow(card) { onCardClick(card) } }
            }
        }
    }
}

@Composable
private fun MobileMediaRow(card: MediaCard, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
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
                text = card.title.ifBlank { "未命名" },
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
        Text("直播", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("打开直播频道列表，选择频道开始观看", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpen) { Text("打开直播") }
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
        Button(onClick = onRetry) { Text("重试") }
    }
}
