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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
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
import com.onefilm.android.tv.compose.design.PosterStyleController

/** 海报样式：竖版 2:3 / 横版 16:9。 */
enum class PosterStyle { Vertical, Horizontal }

/** 标题布局：竖版网格用叠加，横版与首页 rail 用图下。 */
enum class PosterTitleLayout { Overlay, Below }

/**
 * TV 端统一海报卡片。
 *
 * - 竖版 + Overlay：首页网格、分类、搜索、收藏（保持现状）
 * - 竖版 + Below：首页 SectionRow（保持现状）
 * - 横版：16:9 + 标题在图片下方；优先横图，缺失时用「竖图放大暗化背景 + 完整竖图居中」兜底
 */
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    landscapeUrl: String? = null,
    width: Dp? = null,
    sourceLabel: String? = null,
    focusEnabled: Boolean = true,
    style: PosterStyle? = null,
    titleLayout: PosterTitleLayout? = null,
) {
    val horizontal = (style ?: if (PosterStyleController.horizontal) {
        PosterStyle.Horizontal
    } else {
        PosterStyle.Vertical
    }) == PosterStyle.Horizontal
    val layout = titleLayout ?: if (horizontal) PosterTitleLayout.Below else PosterTitleLayout.Overlay
    when {
        horizontal -> HorizontalPoster(
            title = title,
            posterUrl = posterUrl,
            landscapeUrl = landscapeUrl,
            subtitle = subtitle,
            sourceLabel = sourceLabel,
            width = width,
            focusEnabled = focusEnabled,
            onClick = onClick,
            modifier = modifier,
        )
        layout == PosterTitleLayout.Below -> VerticalBelowPoster(
            title = title,
            posterUrl = posterUrl,
            subtitle = subtitle,
            width = width,
            focusEnabled = focusEnabled,
            onClick = onClick,
            modifier = modifier,
        )
        else -> VerticalOverlayPoster(
            title = title,
            posterUrl = posterUrl,
            subtitle = subtitle,
            sourceLabel = sourceLabel,
            width = width,
            focusEnabled = focusEnabled,
            onClick = onClick,
            modifier = modifier,
        )
    }
}

@Composable
private fun VerticalOverlayPoster(
    title: String,
    posterUrl: String?,
    subtitle: String?,
    sourceLabel: String?,
    width: Dp?,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.1f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 320f,
            visibilityThreshold = 0.0005f,
        ),
        label = "poster-focus",
    )
    val shape = RoundedCornerShape(12.dp)
    val sizeModifier = if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()
    Box(
        modifier
            .then(sizeModifier)
            .focusProperties { canFocus = focusEnabled }
            .zIndex(if (focused) 1f else 0f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.8f), shape),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                RemoteImage(
                    rawUrl = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    requestWidth = 480,
                    requestHeight = 720,
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
                sourceLabel?.takeIf { it.isNotBlank() }?.let { label ->
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.detail_unnamed) },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerticalBelowPoster(
    title: String,
    posterUrl: String?,
    subtitle: String?,
    width: Dp?,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.width(140.dp))
            .focusProperties { canFocus = focusEnabled },
    ) {
        FocusableSurface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentDescription = title,
            onClick = onClick,
        ) {
            if (posterUrl.isNullOrBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_no_poster), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                RemoteImage(
                    rawUrl = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    requestWidth = 480,
                    requestHeight = 720,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title.ifBlank { stringResource(R.string.detail_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HorizontalPoster(
    title: String,
    posterUrl: String?,
    landscapeUrl: String?,
    subtitle: String?,
    sourceLabel: String?,
    width: Dp?,
    focusEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = 320f,
            visibilityThreshold = 0.0005f,
        ),
        label = "poster-focus-h",
    )
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .focusProperties { canFocus = focusEnabled }
            .zIndex(if (focused) 1f else 0f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(shape)
                .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.8f), shape)
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (!landscapeUrl.isNullOrBlank()) {
                RemoteImage(
                    rawUrl = landscapeUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    requestWidth = 640,
                    requestHeight = 360,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LandscapeFallback(posterUrl = posterUrl, title = title)
            }
            sourceLabel?.takeIf { it.isNotBlank() }?.let { label ->
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title.ifBlank { stringResource(R.string.detail_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 无横图兜底：竖图放大暗化作背景，原竖图完整居中，不裁切。 */
@Composable
private fun LandscapeFallback(posterUrl: String?, title: String) {
    Box(Modifier.fillMaxSize()) {
        if (posterUrl.isNullOrBlank()) {
            Text(
                text = stringResource(R.string.common_no_poster),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        RemoteImage(
            rawUrl = posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            requestWidth = 320,
            requestHeight = 180,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.25f
                    scaleY = 1.25f
                    alpha = 0.5f
                },
        )
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        RemoteImage(
            rawUrl = posterUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            requestWidth = 300,
            requestHeight = 450,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
        )
    }
}
