package com.onefilm.android.tv.compose.feature.player

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

private val Ink = Color(0xFFF5F5F7)
private val InkDim = Color(0x8AF5F5F7)
private val InkFaint = Color(0x4DF5F5F7)
private val Panel = Color(0xF21C1C1E)
private val Press = Color(0x1FFFFFFF)

private data class MenuEntry(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit = {},
)

/** 视频画面：详情页小窗和全屏共用同一个 ExoPlayer。 */
@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    val player = PlaybackSession.exoPlayer
    if (player == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("暂无播放内容", color = Ink)
        }
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                layoutParams = ViewGroup.LayoutParams(-1, -1)
            }
        },
        update = { it.player = PlaybackSession.exoPlayer },
    )
}

/**
 * iOS 扁平化控制层：黑白灰、无边框、Canvas 图标、自定义细进度条。
 * 单击显隐；小窗双击进入全屏。
 */
@Composable
fun PlayerControls(
    inline: Boolean,
    onToggleFullscreen: () -> Unit,
    onBack: (() -> Unit)? = null,
    onSelectEpisode: ((Int) -> Unit)? = null,
    onPrevious: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val player = PlaybackSession.exoPlayer
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(PlaybackSession.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var overflowEntries by remember { mutableStateOf<List<MenuEntry>>(emptyList()) }
    var danmakuOn by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }

    val menuEntries = buildList {
        add(MenuEntry("上集", onPrevious != null) { onPrevious?.invoke() })
        add(MenuEntry("下集", onNext != null) { onNext?.invoke() })
        add(MenuEntry("重播") { PlaybackSession.replay() })
        add(MenuEntry("刷新") { PlaybackSession.refresh() })
        add(MenuEntry("选集", PlaybackSession.queue.size > 1) { showEpisodes = true })
        add(MenuEntry("${PlaybackSession.speed}x") { PlaybackSession.cycleSpeed() })
        add(
            MenuEntry(
                if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) "循环 开" else "循环 关",
            ) { PlaybackSession.toggleRepeat() },
        )
        add(MenuEntry(if (danmakuOn) "弹幕 开" else "弹幕 关") { danmakuOn = !danmakuOn })
        add(MenuEntry("解码 硬解"))
        add(MenuEntry("渲染 Surface"))
        add(MenuEntry("字幕 默认"))
        add(MenuEntry("音轨 默认"))
        add(MenuEntry("片头 00:00"))
        add(MenuEntry("片尾 00:00"))
        add(MenuEntry("画面 适应"))
    }

    LaunchedEffect(player, controlsVisible, PlaybackSession.isPlaying, showEpisodes, showMore) {
        if (controlsVisible && PlaybackSession.isPlaying && !showEpisodes && !showMore) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(player, controlsVisible, isTv) {
        if (controlsVisible && isTv) runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (!dragging) position = PlaybackSession.positionMs()
            tick++
            if (tick % 10 == 0) PlaybackSession.saveHistory()
            delay(500)
        }
    }
    DisposableEffect(player) {
        onDispose { PlaybackSession.saveHistory() }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(inline) {
                detectTapGestures(
                    onTap = {
                        when {
                            showEpisodes -> showEpisodes = false
                            showMore -> showMore = false
                            else -> controlsVisible = !controlsVisible
                        }
                    },
                    onDoubleTap = { if (inline) onToggleFullscreen() },
                )
            },
    ) {
        if (PlaybackSession.isBuffering && PlaybackSession.error == null) {
            CircularProgressIndicator(
                color = Ink,
                strokeWidth = 2.dp,
                modifier = Modifier.align(Alignment.Center).size(30.dp),
            )
        }
        PlaybackSession.error?.let { message ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = Ink, fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                TextAction("重试") { PlaybackSession.retry() }
            }
        }

        if (controlsVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x8C000000), Color(0x00000000), Color(0x8C000000)),
                    ),
                ),
            )
            if (!inline) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconAction(PlayerGlyph.Back, 42.dp) { onBack() }
                        Spacer(Modifier.width(2.dp))
                    }
                    Text(
                        text = PlaybackSession.title.ifBlank { "正在播放" },
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(PlayerGlyph.More, 42.dp) {
                        overflowEntries = menuEntries
                        showMore = true
                    }
                }
            }

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(if (inline) 18.dp else 34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!inline) {
                    IconAction(PlayerGlyph.Previous, 46.dp, enabled = onPrevious != null) {
                        onPrevious?.invoke()
                    }
                }
                Box(
                    Modifier
                        .size(if (inline) 46.dp else 64.dp)
                        .focusRequester(playFocus)
                        .focusable()
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { PlaybackSession.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerIcon(
                        glyph = if (PlaybackSession.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play,
                        tint = Ink,
                        modifier = Modifier.size(if (inline) 20.dp else 30.dp),
                    )
                }
                if (!inline) {
                    IconAction(PlayerGlyph.Next, 46.dp, enabled = onNext != null) {
                        onNext?.invoke()
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(position),
                        color = InkDim,
                        fontSize = 11.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTime(PlaybackSession.durationMs),
                        color = InkFaint,
                        fontSize = 11.sp,
                    )
                }
                SeekBar(
                    position = position,
                    duration = PlaybackSession.durationMs,
                    onPreview = { position = it },
                    onCommit = { PlaybackSession.seekTo(it) },
                )
                MenuRow(
                    entries = menuEntries,
                    inline = inline,
                    onMore = { hidden ->
                        overflowEntries = hidden
                        showMore = true
                    },
                    onFullscreen = onToggleFullscreen,
                )
            }
        }

        if (showEpisodes) {
            PanelScrim(onDismiss = { showEpisodes = false }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "选集 · ${PlaybackSession.queue.size} 集",
                        color = Ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.weight(1f))
                    TextAction("关闭") { showEpisodes = false }
                }
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(76.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.queue) { index, episode ->
                        val selected = index == PlaybackSession.currentIndex
                        FlatTile(
                            text = episode.name.ifBlank { "${index + 1}" },
                            emphasis = selected,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            showEpisodes = false
                            onSelectEpisode?.invoke(index)
                        }
                    }
                }
            }
        }

        if (showMore) {
            MorePanel(
                entries = overflowEntries,
                onDismiss = { showMore = false },
            )
        }
    }
}

/** 详情页顶部小窗播放器。 */
@Composable
fun InlinePlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize())
        PlayerControls(
            inline = true,
            onToggleFullscreen = onExpand,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** 自定义细进度条：白色 2dp 轨道 + 白色圆点，不依赖 Material Slider。 */
@Composable
private fun SeekBar(
    position: Long,
    duration: Long,
    onPreview: (Long) -> Unit,
    onCommit: (Long) -> Unit,
) {
    val safeDuration = duration.coerceAtLeast(1L)
    var width by remember { mutableLongStateOf(1L) }
    var preview by remember { mutableLongStateOf(position) }
    var dragging by remember { mutableStateOf(false) }
    fun valueAt(x: Float): Long =
        ((x / width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) * safeDuration).toLong()

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
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
        val trackH = 2.dp.toPx()
        val fraction = (if (dragging) preview else position).toFloat()
            .coerceIn(0f, safeDuration.toFloat()) / safeDuration.toFloat()
        drawRoundRect(
            color = Ink.copy(alpha = 0.22f),
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
            radius = if (dragging) 7.dp.toPx() else 5.dp.toPx(),
            center = Offset(size.width * fraction, centerY),
        )
    }
}

@Composable
private fun MenuRow(
    entries: List<MenuEntry>,
    inline: Boolean,
    onMore: (List<MenuEntry>) -> Unit,
    onFullscreen: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val itemWidth = if (inline) 58.dp else 64.dp
        val fullscreenWidth = 34.dp
        val moreWidth = 44.dp
        val available = (maxWidth - fullscreenWidth).coerceAtLeast(0.dp)
        var visible = (available / itemWidth).toInt().coerceAtLeast(0)
        val hasOverflow = visible < entries.size
        if (hasOverflow) {
            visible = ((available - moreWidth) / itemWidth).toInt().coerceAtLeast(0)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.take(visible).forEach { entry ->
                MenuText(entry)
            }
            if (hasOverflow) {
                MenuText(MenuEntry("更多") { onMore(entries.drop(visible)) })
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                glyph = if (inline) PlayerGlyph.Fullscreen else PlayerGlyph.ExitFullscreen,
                size = fullscreenWidth,
            ) { onFullscreen() }
        }
    }
}

/** 进度条下方的紧凑菜单项：一行一个功能，无边框。 */
@Composable
private fun MenuText(entry: MenuEntry) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (focused) Press else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = entry.enabled,
                onClick = entry.onClick,
            )
            .padding(horizontal = 5.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = entry.label,
            color = if (entry.enabled) InkDim else InkFaint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun MorePanel(
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
) {
    PanelScrim(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("更多", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.weight(1f))
            TextAction("关闭", onClick = onDismiss)
        }
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(96.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
        ) {
            items(entries) { entry ->
                FlatTile(entry.label, enabled = entry.enabled) {
                    entry.onClick()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun PanelScrim(onDismiss: () -> Unit, content: @Composable () -> Unit) {
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

/** 纯图标按钮：无边框、无底色，仅焦点态显示极淡白色。 */
@Composable
private fun IconAction(
    glyph: PlayerGlyph,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .size(size)
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
            tint = if (enabled) Ink else InkFaint,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/** 文本按钮：扁平、无边框，选中态才用白色底。 */
@Composable
private fun TextAction(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Press else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(text, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun FlatTile(
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

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
