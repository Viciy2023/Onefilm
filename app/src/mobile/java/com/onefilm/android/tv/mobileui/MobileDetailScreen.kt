package com.onefilm.android.tv.mobileui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.R as AppR
import com.onefilm.android.tv.compose.R as ComposeR
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DetailCache
import com.onefilm.android.tv.compose.bridge.DetailInfo
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.feature.detail.DetailUiState
import com.onefilm.android.tv.compose.feature.detail.DetailViewModel
import com.onefilm.android.tv.compose.feature.player.InlinePlayer
import com.onefilm.android.tv.compose.feature.player.PlaybackEpisode
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.mobileui.component.MobileRemoteImage
import com.onefilm.android.tv.mobileui.player.MobileVideoSurface
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * 手机竖屏详情页：
 * - 上半区域是本剧的小播放器（进入详情自动起播，历史集优先）；
 * - 榜单类条目没有播放源时，在本页内搜索可用站点并自动选第一个，结果列在页面下方，可随时换站；
 * - 全程不跳转中间页。
 */
@Composable
fun MobileDetailScreen(
    key: String,
    id: String,
    title: String,
    poster: String?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: DetailViewModel = viewModel(
        key = "$key:$id",
        factory = viewModelFactory {
            initializer {
                DetailViewModel(key, id, ComposeDependencies.detailDataSource, DetailCache.get(key, id))
            }
        },
    )
    val state by viewModel.state.collectAsState()
    val cachedSite = MobileDetailSiteCache.get(key, id)
    var favorite by remember(key, id) {
        val fk = cachedSite?.key ?: key
        val fi = cachedSite?.id ?: id
        mutableStateOf(runCatching { ComposeDependencies.favoriteStore.isFavorite(fk, fi) }.getOrDefault(false))
    }
    var selectedFlagName by remember(key, id) { mutableStateOf<String?>(null) }
    var selectedEpisodeUrl by remember(key, id) { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var autoStarted by remember(key, id) { mutableStateOf(false) }
    /** 榜单条目在详情页内切换到的可用站点详情（离开详情再回来时恢复，避免重复搜索/切站）。 */
    var siteInfo by remember(key, id) { mutableStateOf(cachedSite) }
    var siteResults by remember(key, id) { mutableStateOf<List<MediaCard>>(emptyList()) }
    var siteSearching by remember { mutableStateOf(false) }
    var siteLoading by remember { mutableStateOf(false) }

    val baseInfo = (state as? DetailUiState.Content)?.info
    val info = siteInfo ?: baseInfo

    LaunchedEffect(key, id) {
        if (DetailCache.get(key, id) == null) viewModel.retry()
    }

    // 换站点：直接在本页加载该站点的详情，不跳转、不重建页面。
    fun selectSite(card: MediaCard, auto: Boolean = false) {
        val currentKey = info?.key ?: key
        val currentId = info?.id ?: id
        if (card.key == currentKey && card.id == currentId) return
        scope.launch {
            siteLoading = true
            val loaded = runCatching {
                ComposeDependencies.detailDataSource.load(card.key, card.id)
            }.getOrNull()
            siteLoading = false
            if (loaded == null || loaded.flags.none { it.episodes.isNotEmpty() }) {
                if (!auto) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(AppR.string.mobile_no_source),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                return@launch
            }
            siteInfo = loaded
            MobileDetailSiteCache.put(key, id, loaded)
            favorite = runCatching {
                ComposeDependencies.favoriteStore.isFavorite(loaded.key, loaded.id)
            }.getOrDefault(false)
            selectedFlagName = loaded.flags.firstOrNull()?.name
            selectedEpisodeUrl = loaded.flags.firstOrNull()?.episodes?.firstOrNull()?.url
            autoStarted = false
        }
    }

    // 榜单类条目没有播放源：在详情页内搜索可用站点，结果列在下方，并自动选第一个（不跳页）。
    LaunchedEffect(baseInfo?.key, baseInfo?.id) {
        val base = baseInfo ?: return@LaunchedEffect
        if (base.flags.any { it.episodes.isNotEmpty() } || base.title.isBlank()) return@LaunchedEffect
        if (siteResults.isNotEmpty() || siteSearching) return@LaunchedEffect
        siteSearching = true
        val collected = mutableListOf<MediaCard>()
        runCatching {
            kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                ComposeDependencies.searchDataSource.search(base.title).collect { progress ->
                    if (progress.items.isNotEmpty()) {
                        collected.clear()
                        collected.addAll(progress.items)
                    }
                }
            }
        }
        siteResults = collected
            .filter { it.key.isNotBlank() && it.id.isNotBlank() && !(it.key == key && it.id == id) }
            .distinctBy { "${it.key}@${it.id}" }
            .take(12)
        siteSearching = false
        if (siteInfo == null) {
            siteResults.firstOrNull()?.let { selectSite(it, auto = true) }
        }
    }

    // 详情数据变化（首次加载或换站点）时，重置线路/选集选择。
    LaunchedEffect(info?.key, info?.id) {
        val data = info ?: return@LaunchedEffect
        val playKey = data.key.ifBlank { key }
        val playId = data.id.ifBlank { id }
        val history = runCatching { ComposeDependencies.playbackHistoryStore.find(playKey, playId) }.getOrNull()
        val savedFlag = history?.let { saved ->
            data.flags.firstOrNull { flag -> flag.episodes.any { it.url == saved.episodeUrl } }
        }
        val flag = savedFlag ?: data.flags.firstOrNull()
        selectedFlagName = flag?.name
        selectedEpisodeUrl = flag?.episodes?.firstOrNull { it.url == history?.episodeUrl }?.url
            ?: flag?.episodes?.firstOrNull()?.url
    }

    fun startPlay(episodeUrl: String? = selectedEpisodeUrl, openPlayer: Boolean = true) {
        val data = info ?: return
        val playKey = data.key.ifBlank { key }
        val playId = data.id.ifBlank { id }
        val flag = data.flags.firstOrNull { it.name == selectedFlagName } ?: data.flags.firstOrNull() ?: return
        val episode = flag.episodes.firstOrNull { it.url == episodeUrl } ?: flag.episodes.firstOrNull() ?: return
        val index = flag.episodes.indexOf(episode).coerceAtLeast(0)
        PlaybackSession.setQueue(
            key = playKey,
            id = playId,
            vodTitle = data.title,
            vodPoster = data.posterUrl,
            flag = flag.name,
            episodes = flag.episodes.map { PlaybackEpisode(it.name, it.url) },
            index = index,
            vodYear = data.year.orEmpty(),
            vodDesc = data.description.orEmpty(),
        )
        PlaybackSession.setAlternatives(
            data.flags.map { detailFlag ->
                detailFlag.name to detailFlag.episodes.map { PlaybackEpisode(it.name, it.url) }
            },
        )
        val history = runCatching { ComposeDependencies.playbackHistoryStore.find(playKey, playId) }.getOrNull()
        val start = if (history?.episodeUrl == episode.url) {
            (history.positionMs - 10_000L).coerceAtLeast(0L)
        } else {
            0L
        }
        PlaybackSession.setMarks(history?.openingMs ?: 0L, history?.endingMs ?: 0L)
        starting = true
        scope.launch {
            val resolved = PlaybackSession.resolvePlayable(
                ComposeDependencies.playbackResolver,
                playKey,
                flag.name,
                episode.url,
            )
            starting = false
            if (resolved != null) {
                PlaybackSession.play(context, resolved.url, start, resolved.headers, resolved.options)
                PlaybackSession.preloadNext(ComposeDependencies.playbackResolver)
                if (openPlayer) onOpenPlayer()
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(ComposeR.string.player_play_failed),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    // 进入详情自动起播（历史集优先）；从全屏返回时同一部剧还在播则不重播。
    LaunchedEffect(info?.key, info?.id, selectedEpisodeUrl) {
        val data = info ?: return@LaunchedEffect
        if (autoStarted) return@LaunchedEffect
        val playKey = data.key.ifBlank { key }
        val playId = data.id.ifBlank { id }
        if (PlaybackSession.siteKey == playKey && PlaybackSession.vodId == playId && PlaybackSession.player != null) {
            autoStarted = true
            return@LaunchedEffect
        }
        if (data.flags.none { it.episodes.isNotEmpty() }) return@LaunchedEffect
        val episode = selectedEpisodeUrl ?: return@LaunchedEffect
        autoStarted = true
        startPlay(episode, openPlayer = false)
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = state) {
            DetailUiState.Loading -> Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "‹",
                        fontSize = 34.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 8.dp),
                    )
                    Text(
                        title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    MobileRemoteImage(
                        rawUrl = poster,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                    )
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            is DetailUiState.Error -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(ComposeR.string.detail_load_failed),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { viewModel.retry() }) {
                    Text(stringResource(ComposeR.string.detail_retry))
                }
                TextButton(onClick = onBack) {
                    Text(stringResource(ComposeR.string.common_back))
                }
            }

            is DetailUiState.Content -> {
                val data = info ?: current.info
                val playKey = data.key.ifBlank { key }
                val playId = data.id.ifBlank { id }
                val flag = data.flags.firstOrNull { it.name == selectedFlagName } ?: data.flags.firstOrNull()
                val episodes = flag?.episodes.orEmpty()
                val searchingSite = siteInfo == null &&
                    data.flags.none { it.episodes.isNotEmpty() } &&
                    siteSearching
                val playing = PlaybackSession.siteKey == playKey &&
                    PlaybackSession.vodId == playId &&
                    PlaybackSession.player != null
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "‹",
                                    fontSize = 34.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onBack)
                                        .padding(horizontal = 8.dp),
                                )
                                Text(
                                    data.title.ifBlank { title },
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            if (playing) {
                                InlinePlayer(
                                    onExpand = onOpenPlayer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp)),
                                    video = { MobileVideoSurface(Modifier.fillMaxSize()) },
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    MobileRemoteImage(
                                        rawUrl = data.landscapeUrl?.takeIf { it.isNotBlank() } ?: data.posterUrl ?: poster,
                                        contentDescription = data.title,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    if (starting || siteLoading || siteSearching) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                modifier = Modifier.size(28.dp),
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                stringResource(ComposeR.string.player_loading_hint),
                                                color = Color.White,
                                                fontSize = 12.sp,
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                data.title.ifBlank { title },
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            val meta = listOfNotNull(
                                data.year?.takeIf { it.isNotBlank() },
                                data.area?.takeIf { it.isNotBlank() },
                                data.remarks?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ")
                            if (meta.isNotBlank()) {
                                Text(
                                    meta,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                            if (!data.description.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    data.description.orEmpty(),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { startPlay(openPlayer = false) },
                                    enabled = !starting && !siteLoading && !siteSearching && episodes.isNotEmpty(),
                                ) {
                                    Text(stringResource(AppR.string.play))
                                }
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            ComposeDependencies.favoriteStore.toggle(playKey, playId, "", data.title, data.posterUrl)
                                            favorite = ComposeDependencies.favoriteStore.isFavorite(playKey, playId)
                                        }
                                    },
                                ) {
                                    Text(
                                        if (favorite) stringResource(AppR.string.keep_add)
                                        else stringResource(AppR.string.keep),
                                    )
                                }
                            }
                            if (data.flags.size > 1) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(AppR.string.mobile_line_title),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    data.flags.forEach { item ->
                                        item {
                                            val selected = item.name == (flag?.name ?: data.flags.firstOrNull()?.name)
                                            Text(
                                                item.name,
                                                fontSize = 12.sp,
                                                color = if (selected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .background(
                                                        if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                    )
                                                    .clickable {
                                                        selectedFlagName = item.name
                                                        selectedEpisodeUrl = item.episodes.firstOrNull()?.url
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                            )
                                        }
                                    }
                                }
                            }
                            if (!searchingSite) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    stringResource(ComposeR.string.player_select_episode),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (episodes.isEmpty()) {
                                    Text(
                                        stringResource(AppR.string.mobile_no_source),
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    itemsIndexed(episodes) { index, episode ->
                        val selected = episode.url == selectedEpisodeUrl
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                )
                                .clickable {
                                    selectedEpisodeUrl = episode.url
                                    startPlay(episode.url, openPlayer = false)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                episode.name.ifBlank { "${index + 1}" },
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    if (siteResults.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(Modifier.padding(top = 10.dp)) {
                                Text(
                                    stringResource(AppR.string.mobile_other_sites),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                siteResults.forEach { card ->
                                    val selected = card.key == playKey && card.id == playId
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                else Color.Transparent,
                                            )
                                            .clickable { selectSite(card) }
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                card.siteName ?: card.key,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                            Text(
                                                card.title.ifBlank { card.id },
                                                fontSize = 13.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                            )
                                            val sub = card.subtitle
                                            if (!sub.isNullOrBlank()) {
                                                Text(
                                                    sub,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                )
                                            }
                                        }
                                        if (selected) {
                                            Text(
                                                "✓",
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 榜单条目选中的站点详情缓存：返回详情页时不再重新搜索/切站。 */
private object MobileDetailSiteCache {
    private val map = java.util.concurrent.ConcurrentHashMap<String, DetailInfo>()

    fun get(key: String, id: String): DetailInfo? = map["$key@$id"]

    fun put(key: String, id: String, info: DetailInfo) {
        map["$key@$id"] = info
    }
}
