package com.onefilm.android.tv.mobileui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.R
import androidx.compose.foundation.lazy.items

/**
 * Mobile 端统一海报卡片：2:3 海报 + 底部渐变遮罩上的标题/评分，
 * 站点名（可选）叠在卡片顶部，与 TV 的 PosterCard 保持一致。
 */
@Composable
fun MobilePosterCard(
    card: MediaCard,
    width: Dp,
    height: Dp,
    showSite: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        MobileRemoteImage(
            rawUrl = card.posterUrl,
            contentDescription = card.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp, start = 4.dp, end = 4.dp)
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
                .padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.title.ifBlank { stringResource(R.string.mobile_unnamed) },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            card.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
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
 * 固定行数的横向网格：左右连续滑动，每列是一个纵向 strip。
 *
 * @param rows 每屏行数
 * @param rowHeight 单个卡片高度（= 行高）
 * @param cardWidth 单个卡片宽度
 */
@Composable
fun MobileHorizontalGrid(
    items: List<MediaCard>,
    rows: Int,
    rowHeight: Dp,
    cardWidth: Dp,
    rowGap: Dp = 8.dp,
    colGap: Dp = 8.dp,
    showSite: Boolean = false,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onCardClick: (MediaCard) -> Unit,
) {
    if (items.isEmpty() || rows <= 0) return
    val strips = items.chunked(rows)
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight * rows + rowGap * (rows - 1)),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(colGap),
    ) {
        items(strips.size) { index ->
            val strip = strips[index]
            Column(
                Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                strip.forEach { card ->
                    MobilePosterCard(
                        card = card,
                        width = cardWidth,
                        height = rowHeight,
                        showSite = showSite,
                        onClick = { onCardClick(card) },
                    )
                }
                repeat(rows - strip.size) {
                    Spacer(Modifier.height(rowHeight))
                }
            }
        }
    }
}
