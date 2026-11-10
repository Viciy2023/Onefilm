package com.onefilm.android.tv.mobileui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.R
import com.onefilm.android.tv.compose.bridge.MediaCard

/**
 * Mobile 横版海报卡片：16:9 图片，标题/副标题叠在图片内底部（与 TV 横版海报一致）。
 * 仅在「海报样式=横版」时使用。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MobileLandscapeCard(
    card: MediaCard,
    modifier: Modifier = Modifier,
    showSite: Boolean = false,
    hero: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    // 与 TV 横版海报一致：优先用横版剧照；没有横版图、或加载到的其实是竖图时，
    // 改为「放大压暗的竖版海报做背景 + 居中完整显示竖版海报」，避免直接把竖版海报裁成 16:9。
    val landscapeUrl = card.landscapeUrl?.takeIf { it.isNotBlank() && it != card.posterUrl }
    var landscapeOk by remember(card.landscapeUrl, card.posterUrl) {
        mutableStateOf(landscapeUrl != null)
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (landscapeOk && landscapeUrl != null) {
            MobileRemoteImage(
                rawUrl = landscapeUrl,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSize = { width, height ->
                    if (width > 0 && height > 0 && width <= height) landscapeOk = false
                },
            )
        } else {
            MobileLandscapeFallback(posterUrl = card.posterUrl, title = card.title)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x33000000), Color(0xE6000000)),
                    ),
                ),
        )
        if (showSite) {
            val site = card.siteName.orEmpty()
            if (site.isNotBlank()) {
                Text(
                    text = site,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    start = if (hero) 14.dp else 8.dp,
                    end = if (hero) 14.dp else 8.dp,
                    bottom = 2.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (hero) 3.dp else 1.dp),
        ) {
            Text(
                text = card.title.ifBlank { stringResource(R.string.mobile_unnamed) },
                color = Color.White,
                fontSize = if (hero) 20.sp else 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val sub = card.subtitle?.takeIf { it.isNotBlank() }
                ?: card.siteName?.takeIf { it.isNotBlank() }
            if (sub != null) {
                Text(
                    text = sub,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = if (hero) 13.sp else 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 横版海报网格：竖屏固定 2 列，一屏行数按可用高度自然滚动。
 * 首页、分类页、搜索结果、收藏/历史共用。
 */
@Composable
fun MobileLandscapeGrid(
    items: List<MediaCard>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    showSite: Boolean = false,
    onCardClick: (MediaCard) -> Unit,
    onCardLongClick: ((MediaCard) -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(items) { _, card ->
            MobileLandscapeCard(
                card = card,
                showSite = showSite,
                onClick = { onCardClick(card) },
                onLongClick = onCardLongClick?.let { callback -> { callback(card) } },
            )
        }
    }
}


/** 没有横版剧照时的兜底：竖版海报放大压暗做背景 + 居中完整显示竖版海报（与 TV 一致）。 */
@Composable
private fun MobileLandscapeFallback(posterUrl: String?, title: String) {
    if (posterUrl.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
        }
        return
    }
    MobileRemoteImage(
        rawUrl = posterUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = 1.25f
                scaleY = 1.25f
                alpha = 0.5f
            },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
    MobileRemoteImage(
        rawUrl = posterUrl,
        contentDescription = title,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
    )
}
