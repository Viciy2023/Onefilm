package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.onefilm.android.tv.compose.bridge.DetailInfo
import com.onefilm.android.tv.compose.design.component.AppLogoLoading
import com.onefilm.android.tv.compose.design.component.RemoteImage
import com.onefilm.android.tv.compose.feature.player.InlinePlayer
import kotlinx.coroutines.delay

@Composable
fun DetailScreen(
    state: DetailUiState,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlay: (flagName: String?, episodeUrl: String?) -> Unit,
    favorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    playingUrl: String? = null,
    onExpandPlayer: () -> Unit = {},
    previewTitle: String? = null,
    previewPoster: String? = null,
    playingEpisodeUrl: String? = null,
) {
    when (state) {
        DetailUiState.Loading -> {
            if (!previewTitle.isNullOrBlank()) {
                DetailPlaceholder(
                    title = previewTitle,
                    posterUrl = previewPoster,
                    onBack = onBack,
                    loading = true,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AppLogoLoading(size = 84.dp)
                }
            }
        }
        is DetailUiState.Error -> {
            DetailPlaceholder(
                title = previewTitle.orEmpty().ifBlank { "影片详情" },
                posterUrl = previewPoster,
                onBack = onBack,
                loading = false,
                message = "详情加载失败，请重试",
                onRetry = onRetry,
            )
        }
        is DetailUiState.Content -> DetailContent(
            state.info,
            onBack,
            onPlay,
            favorite,
            onToggleFavorite,
            playingUrl,
            onExpandPlayer,
            playingEpisodeUrl,
        )
    }
}

@Composable
private fun DetailPlaceholder(
    title: String,
    posterUrl: String?,
    onBack: () -> Unit,
    loading: Boolean,
    message: String? = null,
    onRetry: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            RemoteImage(
                rawUrl = posterUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0x44000000), Color(0xCC000000)))),
            )
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (loading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AppLogoLoading(size = 84.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载剧集…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message.orEmpty(), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    info: DetailInfo,
    onBack: () -> Unit,
    onPlay: (String?, String?) -> Unit,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    playingUrl: String?,
    onExpandPlayer: () -> Unit,
    playingEpisodeUrl: String?,
) {
    var selectedFlagIndex by remember(info.title) { mutableIntStateOf(0) }
    var manualFlagSelection by remember(info.title) { mutableStateOf(false) }
    var reversed by remember(info.title) { mutableStateOf(false) }
    var inlineReady by remember(info.title) { mutableStateOf(false) }
    LaunchedEffect(playingUrl, info.title) {
        inlineReady = false
        if (playingUrl != null) {
            delay(250)
            inlineReady = true
        }
    }
    val selectedFlag = info.flags.getOrNull(selectedFlagIndex) ?: info.flags.firstOrNull()
    val episodes = selectedFlag?.episodes.orEmpty()
    val visibleEpisodes = if (reversed) episodes.reversed() else episodes

    LaunchedEffect(playingEpisodeUrl) {
        if (!manualFlagSelection && playingEpisodeUrl != null) {
            val index = info.flags.indexOfFirst { flag -> flag.episodes.any { it.url == playingEpisodeUrl } }
            if (index >= 0) selectedFlagIndex = index
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
        Row(
            Modifier.fillMaxWidth().weight(1.15f),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                Modifier
                    .weight(0.60f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = info.title.ifBlank { "未命名" },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append("来源：")
                        append(info.sourceName ?: info.key)
                        info.year?.let { append("    年份：$it") }
                        info.area?.let { append("    地区：$it") }
                        info.remarks?.let { append("    $it") }
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                info.actor?.let {
                    Text(
                        text = "演员：$it",
                        fontSize = 24.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                info.director?.let {
                    Text(
                        text = "导演：$it",
                        fontSize = 24.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val address = selectedFlag?.episodes?.firstOrNull()?.url ?: info.playUrl
                if (!address.isNullOrBlank()) {
                    Text(
                        text = "播放地址：$address",
                        fontSize = 21.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                info.description?.let {
                    Text(
                        text = "内容简介：$it",
                        fontSize = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { reversed = !reversed }) {
                        Text(if (reversed) "正序" else "倒序", fontSize = 24.sp)
                    }
                    Button(onClick = onToggleFavorite) {
                        Text(if (favorite) "已收藏" else "加入收藏", fontSize = 24.sp)
                    }
                }
            }
            Box(
                Modifier
                    .weight(0.40f)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black),
            ) {
                if (playingUrl != null) {
                    if (inlineReady) {
                        InlinePlayer(onExpand = onExpandPlayer, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            AppLogoLoading(size = 76.dp)
                        }
                    }
                } else {
                    if (!info.posterUrl.isNullOrBlank()) {
                        RemoteImage(
                            rawUrl = info.posterUrl,
                            contentDescription = info.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(Color(0x22000000), Color(0xAA000000)))),
                    )
                    Text(
                        text = "点击播放",
                        color = Color.White,
                        fontSize = 24.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                onPlay(selectedFlag?.name, episodes.firstOrNull()?.url)
                            },
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("线路", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(88.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(info.flags) { index, flag ->
                    val selected = index == selectedFlagIndex
                    Text(
                        text = flag.name.ifBlank { "默认线路" },
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        fontSize = 21.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable {
                                selectedFlagIndex = index
                                manualFlagSelection = true
                                reversed = false
                            }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(visibleEpisodes) { _, episode ->
                val playing = playingEpisodeUrl != null && episode.url == playingEpisodeUrl
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (playing) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { onPlay(selectedFlag?.name, episode.url) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = episode.name.ifBlank { "未命名" },
                        color = if (playing) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        fontSize = 21.sp,
                        fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}