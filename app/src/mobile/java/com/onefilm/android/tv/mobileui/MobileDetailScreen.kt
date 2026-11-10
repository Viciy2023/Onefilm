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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.onefilm.android.tv.compose.R as ComposeR
import com.onefilm.android.tv.R as AppR
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DetailCache
import com.onefilm.android.tv.compose.feature.detail.DetailUiState
import com.onefilm.android.tv.compose.feature.detail.DetailViewModel
import com.onefilm.android.tv.compose.feature.player.PlaybackEpisode
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.mobileui.component.MobileRemoteImage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 手机竖屏详情页：线路 + 选集 + 简介 + 收藏，点集数进入新播放器。 */
@Composable
fun MobileDetailScreen(
    key: String,
    id: String,
    title: String,
    poster: String?,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onSwitchSite: ((key: String, id: String, title: String, poster: String?) -> Unit)? = null,
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
    var favorite by remember(key, id) {
        mutableStateOf(runCatching { ComposeDependencies.favoriteStore.isFavorite(key, id) }.getOrDefault(false))
    }
    var selectedFlagName by remember(key, id) { mutableStateOf<String?>(null) }
    var selectedEpisodeUrl by remember(key, id) { mutableStateOf<String?>(null) }
    var starting by remember { mutableStateOf(false) }
    var fallbackTried by remember(key, id) { mutableStateOf(false) }
    var fallbackSearching by remember { mutableStateOf(false) }

    LaunchedEffect(key, id) {
        if (DetailCache.get(key, id) == null) viewModel.retry()
    }
    val info = (state as? DetailUiState.Content)?.info
    LaunchedEffect(info?.key, info?.id) {
        val data = info ?: return@LaunchedEffect
        val history = runCatching { ComposeDependencies.playbackHistoryStore.find(key, id) }.getOrNull()
        val savedFlag = history?.let { saved ->
            data.flags.firstOrNull { flag -> flag.episodes.any { it.url == saved.episodeUrl } }
        }
        val flag = savedFlag ?: data.flags.firstOrNull()
        if (selectedFlagName == null) selectedFlagName = flag?.name
        val episode = flag?.episodes?.firstOrNull { it.url == history?.episodeUrl }
            ?: flag?.episodes?.firstOrNull()
        if (selectedEpisodeUrl == null) selectedEpisodeUrl = episode?.url
    }

    LaunchedEffect(info?.key, info?.id, info?.flags?.size) {
        val data = info ?: return@LaunchedEffect
        val playable = data.flags.any { it.episodes.isNotEmpty() }
        if (playable || fallbackTried || data.title.isBlank()) return@LaunchedEffect
        // 榜单类条目（豆瓣）没有播放源：按片名搜索其它站点，自动切到第一个有结果的站点。
        fallbackTried = true
        fallbackSearching = true
        val candidate = runCatching {
            ComposeDependencies.searchDataSource.search(data.title)
                .firstOrNull { it.items.isNotEmpty() }
                ?.items
                ?.firstOrNull {
                    it.key.isNotBlank() && it.id.isNotBlank() && !(it.key == key && it.id == id)
                }
        }.getOrNull()
        fallbackSearching = false
        if (candidate != null) {
            onSwitchSite?.invoke(candidate.key, candidate.id, candidate.title, candidate.posterUrl)
        }
    }

    fun startPlay(episodeUrl: String? = selectedEpisodeUrl) {
        val data = info ?: return
        val flag = data.flags.firstOrNull { it.name == selectedFlagName } ?: data.flags.firstOrNull() ?: return
        val episode = flag.episodes.firstOrNull { it.url == episodeUrl } ?: flag.episodes.firstOrNull() ?: return
        val index = flag.episodes.indexOf(episode).coerceAtLeast(0)
        PlaybackSession.setQueue(
            key = key,
            id = id,
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
        val history = runCatching { ComposeDependencies.playbackHistoryStore.find(key, id) }.getOrNull()
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
                key,
                flag.name,
                episode.url,
            )
            starting = false
            if (resolved != null) {
                PlaybackSession.play(context, resolved.url, start, resolved.headers, resolved.options)
                PlaybackSession.preloadNext(ComposeDependencies.playbackResolver)
                onOpenPlayer()
            } else {
                android.widget.Toast.makeText(
                    context,
                    context.getString(ComposeR.string.player_play_failed),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    BackHandler { onBack() }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when (val current = state) {
            DetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
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
                val data = current.info
                val flag = data.flags.firstOrNull { it.name == selectedFlagName } ?: data.flags.firstOrNull()
                val episodes = flag?.episodes.orEmpty()
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
                            MobileRemoteImage(
                                rawUrl = data.landscapeUrl?.takeIf { it.isNotBlank() } ?: data.posterUrl ?: poster,
                                contentDescription = data.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
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
                                Button(onClick = { startPlay() }, enabled = !starting) {
                                    Text(
                                        if (starting) stringResource(ComposeR.string.player_loading_hint)
                                        else stringResource(AppR.string.play),
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            ComposeDependencies.favoriteStore.toggle(key, id, "", data.title, data.posterUrl)
                                            favorite = ComposeDependencies.favoriteStore.isFavorite(key, id)
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
                            Spacer(Modifier.height(12.dp))
                            Text(
                                stringResource(ComposeR.string.player_select_episode),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (episodes.isEmpty()) {
                                Text(
                                    stringResource(
                                        if (fallbackSearching) AppR.string.mobile_searching_source
                                        else AppR.string.mobile_no_source,
                                    ),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
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
                                    startPlay(episode.url)
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
                }
            }
        }
    }
}
