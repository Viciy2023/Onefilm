package com.onefilm.android.tv.compose.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DlnaDevice
import com.onefilm.android.tv.compose.design.component.AppLogoLoading
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * 播放器渲染组件（进度条、菜单行、按钮、弹窗）。
 * P1-4：从 PlayerControls 拆出，仅移动代码，行为不变。
 */

internal val Ink = Color(0xFFF5F5F7)
internal val InkDim = Color(0x8AF5F5F7)
internal val InkFaint = Color(0x4DF5F5F7)
internal val Panel = Color(0xF21C1C1E)
internal val Press = Color(0xFFFFFFFF)

@Composable
internal fun SeekBar(
    position: Long,
    duration: Long,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
) {
    val safeDuration = duration.coerceAtLeast(1L)
    var width by remember { mutableLongStateOf(1L) }
    var preview by remember { mutableLongStateOf(position) }
    var dragging by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    fun valueAt(x: Float): Long =
        ((x / width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) * safeDuration).toLong()
    fun seekBy(deltaMs: Long) {
        val next = ((if (dragging) preview else position) + deltaMs)
            .coerceIn(0L, safeDuration)
        preview = next
        onPreview(next)
        onCommit(next)
    }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        seekBy(-5_000L)
                        true
                    }
                    Key.DirectionRight -> {
                        seekBy(5_000L)
                        true
                    }
                    else -> false
                }
            }
            .onSizeChanged { width = it.width.toLong().coerceAtLeast(1L) }
            .pointerInput(safeDuration) {
                detectTapGestures { offset ->
                    val value = valueAt(offset.x)
                    preview = value
                    onPreview(value)
                    onCommit(value)
                }
            }
            .pointerInput(safeDuration) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        preview = valueAt(offset.x)
                        onPreview(preview)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        preview = valueAt(change.position.x)
                        onPreview(preview)
                    },
                    onDragEnd = {
                        dragging = false
                        onCommit(preview)
                    },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        val centerY = size.height / 2f
        // 扁平焦点态：不加边框/底色，只把轨道加粗、调亮，圆点放大。
        val trackH = (if (focused) 4.dp else 2.dp).toPx()
        val trackAlpha = if (focused) 0.5f else 0.22f
        val fraction = (if (dragging) preview else position).toFloat()
            .coerceIn(0f, safeDuration.toFloat()) / safeDuration.toFloat()
        drawRoundRect(
            color = Ink.copy(alpha = trackAlpha),
            topLeft = Offset(0f, centerY - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        drawRoundRect(
            color = Ink,
            topLeft = Offset(0f, centerY - trackH / 2f),
            size = Size(size.width * fraction, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        drawCircle(
            color = Ink,
            radius = if (dragging || focused) 7.dp.toPx() else 5.dp.toPx(),
            center = Offset(size.width * fraction, centerY),
        )
    }
}

@Composable
internal fun MenuRow(
    entries: List<PlayerMenuEntry>,
    inline: Boolean,
    onMore: (List<PlayerMenuEntry>) -> Unit,
    onFullscreen: () -> Unit,
    fullscreenFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
        val spacing = 4.dp
        val fullscreenWidth = 52.dp
        val moreWidth = 58.dp
        val minWidth = if (inline) 54.dp else 58.dp
        val widths = entries.map { entry ->
            val textWidth = with(density) {
                measurer.measure(entry.label, textStyle).size.width.toDp()
            }
            maxOf(textWidth + 22.dp, minWidth)
        }
        val available = (maxWidth - fullscreenWidth).coerceAtLeast(0.dp)
        var used = 0.dp
        var visible = 0
        for (width in widths) {
            if (used + width + spacing <= available) {
                used += width + spacing
                visible++
            } else {
                break
            }
        }
        var hasOverflow = visible < entries.size
        if (hasOverflow) {
            used = 0.dp
            visible = 0
            val limited = (available - moreWidth).coerceAtLeast(0.dp)
            for (width in widths) {
                if (used + width + spacing <= limited) {
                    used += width + spacing
                    visible++
                } else {
                    break
                }
            }
            hasOverflow = visible < entries.size
        }
        Row(
            Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            var menuRequesterAssigned = false
            val visibleEntries = entries.take(visible)
            val firstEnabledIndex = visibleEntries.indexOfFirst { it.enabled }
            val nextEpisodeIndex = visibleEntries.indexOfFirst { it.enabled && it.label.startsWith("下集") }
            val defaultIndex = if (nextEpisodeIndex >= 0) nextEpisodeIndex else firstEnabledIndex
            visibleEntries.forEachIndexed { index, entry ->
                val requester = if (!menuRequesterAssigned && index == defaultIndex) {
                    menuRequesterAssigned = true
                    firstItemFocusRequester
                } else {
                    null
                }
                MenuText(entry, widths[index], requester)
            }
            if (hasOverflow) {
                MenuText(PlayerMenuEntry("更多") { onMore(entries.drop(visible)) }, moreWidth)
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                glyph = if (inline) PlayerGlyph.Fullscreen else PlayerGlyph.ExitFullscreen,
                size = fullscreenWidth,
                modifier = if (fullscreenFocusRequester != null) {
                    Modifier.focusRequester(fullscreenFocusRequester)
                } else Modifier,
            ) { onFullscreen() }
        }
    }
}

@Composable
internal fun MenuText(entry: PlayerMenuEntry, width: Dp, focusRequester: FocusRequester? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .width(width)
            .height(40.dp)
            .scale(if (focused) 1.06f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Press else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = entry.enabled,
                onClick = entry.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.label,
            color = when {
                focused -> Color.Black
                entry.enabled -> Ink.copy(alpha = 0.92f)
                else -> InkFaint
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun DlnaPopup(
    devices: List<DlnaDevice>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onPick: (DlnaDevice) -> Unit,
) {
    Box(
        Modifier.fillMaxSize().padding(end = 14.dp, bottom = 64.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("投屏设备", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextAction("关闭", onClick = onDismiss)
            }
            Spacer(Modifier.height(6.dp))
            when {
                loading -> Text("正在搜索设备…", color = InkDim, fontSize = 12.sp)
                devices.isEmpty() -> Text("未发现投屏设备", color = InkDim, fontSize = 12.sp)
                else -> devices.forEach { device ->
                    FlatTile(
                        text = device.name.ifBlank { device.id },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) { onPick(device) }
                }
            }
        }
    }
}

@Composable
internal fun OverflowPopup(
    entries: List<PlayerMenuEntry>,
    inline: Boolean,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .padding(end = 14.dp, bottom = if (inline) 58.dp else 64.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Panel)
                .padding(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("更多", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                TextAction("关闭", onClick = onDismiss)
            }
            Spacer(Modifier.height(6.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).focusGroup(),
            ) {
                itemsIndexed(entries) { index, entry ->
                    FlatTile(
                        text = entry.label,
                        enabled = entry.enabled,
                        modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    ) {
                        entry.onClick()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
internal fun PanelScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Panel)
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(18.dp),
        ) {
            content()
        }
    }
}

@Composable
internal fun IconAction(
    glyph: PlayerGlyph,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .size(size)
            .scale(if (focused) 1.08f else 1f)
            .clip(CircleShape)
            .background(if (focused) Press else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        PlayerIcon(
            glyph = glyph,
            tint = if (focused) Color.Black else if (enabled) Ink else InkFaint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

@Composable
internal fun TextAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Press else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (focused) Color.Black else Ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun FlatTile(
    text: String,
    emphasis: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .heightIn(min = 44.dp)
            .background(
                when {
                    emphasis -> Ink
                    focused -> Press
                    else -> Color.White.copy(alpha = 0.06f)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                focused -> Color.Black
                !enabled -> InkFaint
                emphasis -> Color.Black
                else -> Ink
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

