package com.onefilm.android.tv.compose.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.MediaCard

/**
 * TV 首页首行（仅横版海报模式使用）：
 *
 * 左侧英雄卡 = 普通卡 2 列宽 + 1 个列距 × 2 行高 + 1 个行距；
 * 右侧 2×2 四张普通卡，列距/行距与下方网格完全一致，因此左右两侧高度相同。
 */
@Composable
fun TvHeroBlock(
    hero: MediaCard,
    sideCards: List<MediaCard>,
    onCardClick: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
    columnGap: Dp = 12.dp,
    rowGap: Dp = 12.dp,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // 与下方 4 列网格使用同一套尺寸计算：总宽 = 4×卡宽 + 3×列距。
        val cardWidth = (maxWidth - columnGap * 3) / 4
        val cardHeight = cardWidth * 9f / 16f
        val heroWidth = cardWidth * 2 + columnGap
        val heroHeight = cardHeight * 2 + rowGap
        Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
            TvHeroCard(
                card = hero,
                modifier = Modifier.width(heroWidth).height(heroHeight),
                onCardClick = onCardClick,
            )
            Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
                sideCards.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(columnGap)) {
                        rowItems.forEach { card ->
                            PosterCard(
                                title = card.title,
                                posterUrl = card.posterUrl,
                                subtitle = card.subtitle,
                                landscapeUrl = card.landscapeUrl,
                                width = cardWidth,
                                onClick = { onCardClick(card) },
                            )
                        }
                        repeat(2 - rowItems.size) {
                            Box(Modifier.width(cardWidth))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvHeroCard(
    card: MediaCard,
    modifier: Modifier,
    onCardClick: (MediaCard) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
        label = "home-hero-focus",
    )
    val cardShape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .clickable(interactionSource = interaction, indication = null) { onCardClick(card) },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    shape = cardShape
                    clip = true
                    ambientShadowColor = Color.Black
                    spotShadowColor = Color.Black
                    shadowElevation = if (focused) 20.dp.toPx() else 0f
                }
                .border(if (focused) 3.dp else 0.dp, Color.White, cardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant, cardShape),
        ) {
            RemoteImage(
                rawUrl = card.landscapeUrl?.takeIf { it.isNotBlank() } ?: card.posterUrl,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                requestWidth = 960,
                requestHeight = 400,
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
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = card.title.ifBlank { stringResource(R.string.detail_unnamed) },
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                val subtitle = card.subtitle?.takeIf { it.isNotBlank() }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
