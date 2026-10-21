package com.onefilm.android.tv.compose.feature.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.foundation.layout.widthIn
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
    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = "‹ 返回",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Row(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 720.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(160.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    RemoteImage(
                        rawUrl = posterUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = title.ifBlank { "影片详情" },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (loading) {
                    AppLogoLoading(size = 48.dp)
                    Text("正在加载详情…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                } else {
                    Text(
                        text = message.orEmpty().ifBlank { "详情加载失败" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                    )
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
    val firstActionFocus = remember { FocusRequester() }
    LaunchedEffect(info.title) {
        delay(120)
        runCatching { firstActionFocus.requestFocus() }
    }
    LaunchedEffect(playingUrl, info.title) {
        inlineReady = false
        if (playingUrl != null) {
            delay(250)
            inlineReady = true
        }
    }
    val selectedFlag = info.flags.getOrNull(selectedFlagIndex) ?: info.flags.firstOrNull()
    val playingFlagIndex = info.flags.indexOfFirst { flag -> flag.episodes.any { it.url == playingEpisodeUrl } }
    val episodes = selectedFlag?.episodes.orEmpty()
    val visibleEpisodes = if (reversed) episodes.reversed() else episodes

    LaunchedEffect(playingEpisodeUrl) {
        if (!manualFlagSelection && playingEpisodeUrl != null) {
            val index = info.flags.indexOfFirst { flag -> flag.episodes.any { it.url == playingEpisodeUrl } }
            if (index >= 0) selectedFlagIndex = index
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().weight(1.15f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(Modifier.weight(0.60f).fillMaxHeight()) {
                Text(
                    text = info.title.ifBlank { "未命名" },
                    fontSize = 20.sp,
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
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    info.actor?.let {
                        Text("演员：$it", fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    info.director?.let {
                        Text("导演：$it", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val address = selectedFlag?.episodes?.firstOrNull()?.url ?: info.playUrl
                    if (!address.isNullOrBlank()) {
                        Text(
                            "播放地址：$address",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    info.description?.let {
                        Text(
                            "内容简介：$it",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailAction(
                        label = if (reversed) "正序" else "倒序",
                        modifier = Modifier.focusRequester(firstActionFocus),
                    ) {
                        reversed = !reversed
                    }
                    DetailAction(
                        label = if (favorite) "已收藏" else "加入收藏",
                        active = favorite,
                        onClick = onToggleFavorite,
                    )
                }
            }
            Box(
                Modifier
                    .weight(0.40f)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
            ) {
                if (playingUrl != null) {
                    if (inlineReady) {
                        InlinePlayer(onExpand = onExpandPlayer, modifier = Modifier.fillMaxSize())
                    } else {
                        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                            AppLogoLoading(size = 64.dp)
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
                        fontSize = 16.sp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clickable {
                                onPlay(selectedFlag?.name, episodes.firstOrNull()?.url)
                            },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("线路", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(52.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(info.flags) { index, flag ->
                    DetailLineChip(
                        label = flag.name.ifBlank { "默认线路" },
                        selected = index == selectedFlagIndex ||
                            (!manualFlagSelection && index == playingFlagIndex),
                        onClick = {
                            selectedFlagIndex = index
                            manualFlagSelection = true
                            reversed = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(visibleEpisodes) { _, episode ->
                EpisodeButton(
                    label = episode.name.ifBlank { "未命名" },
                    playing = playingEpisodeUrl != null && episode.url == playingEpisodeUrl,
                    onClick = { onPlay(selectedFlag?.name, episode.url) },
                )
            }
        }
    }
}

@Composable
private fun DetailAction(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (focused) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DetailLineChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        color = when {
            focused -> MaterialTheme.colorScheme.background
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        },
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.onSurface
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .border(
                if (focused || selected) 2.dp else 1.dp,
                when {
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun EpisodeButton(
    label: String,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(9.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(
                if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                when {
                    focused -> 2.dp
                    playing -> 1.dp
                    else -> 0.dp
                },
                if (focused) MaterialTheme.colorScheme.primary
                else if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else Color.Transparent,
                shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
