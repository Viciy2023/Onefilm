package com.onefilm.android.tv.compose.design.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

/** 首页英雄卡高度：约为普通横版卡（128dp）的 1.55 倍。 */
val HomeHeroCardHeight: Dp = 200.dp

/**
 * TV 首页首行的英雄卡（仅横版海报模式使用）。
 *
 * 只展示列表第一项，占满整行，高度 [HomeHeroCardHeight]，标题/副标题叠在图片底部并居中，
 * 焦点态与普通海报卡一致（白色描边 + 轻微放大 + 阴影）。其余条目继续以普通卡片展示。
 */
@Composable
fun TvHeroRow(
    items: List<MediaCard>,
    onCardClick: (MediaCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = items.firstOrNull() ?: return
    TvHeroCard(
        card = hero,
        modifier = modifier.fillMaxWidth().height(HomeHeroCardHeight),
        onCardClick = onCardClick,
    )
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
    val cardShape = RoundedCornerShape(16.dp)
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
