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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.onefilm.android.tv.compose.bridge.DetailInfo
import com.onefilm.android.tv.compose.design.component.RemoteImage
import com.onefilm.android.tv.compose.feature.player.InlinePlayer

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
                    CircularProgressIndicator()
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
                    CircularProgressIndicator()
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
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item("hero") {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (playingUrl != null) {
                    InlinePlayer(
                        onExpand = onExpandPlayer,
                        modifier = Modifier.fillMaxSize(),
                    )
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
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000)))),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp),
                    ) {
                        Text(
                            info.title,
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(info.year, info.area, info.remarks).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                        )
                    }
                }
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
            }
        }
        item("actions") {
            Row(
                Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = { onPlay(null, null) }) { Text("播放") }
                OutlinedButton(onClick = onToggleFavorite) { Text(if (favorite) "已收藏" else "收藏") }
            }
        }
        info.description?.let { desc ->
            item("desc") {
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        info.flags.forEach { flag ->
            item("flag:${flag.name}") {
                Column {
                    Text(
                        text = flag.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) {
                        items(flag.episodes) { episode ->
                            Text(
                                text = episode.name.ifBlank { "未命名" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { onPlay(flag.name, episode.url) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
