package com.onefilm.android.tv.compose.feature.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** 视频画面：详情页小窗和全屏共用同一个 PlayerView 渲染的 ExoPlayer。 */
@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    val player = PlaybackSession.exoPlayer
    if (player == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("暂无播放内容", color = Color.White)
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
 * iOS 扁平风格控制层：无边框、图标按钮、白色细进度条。
 * 单击显隐，双击小窗进入全屏。
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
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(PlaybackSession.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var danmakuOn by remember { mutableStateOf(true) }
    var tick by remember { mutableIntStateOf(0) }
    val playFocus = remember { FocusRequester() }

    LaunchedEffect(player, controlsVisible, PlaybackSession.isPlaying, showSpeedMenu, showEpisodes, showMore) {
        if (controlsVisible && PlaybackSession.isPlaying &&
            !showSpeedMenu && !showEpisodes && !showMore
        ) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(player, controlsVisible) {
        if (controlsVisible) runCatching { playFocus.requestFocus() }
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
                            showSpeedMenu -> showSpeedMenu = false
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
                color = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp),
            )
        }
        PlaybackSession.error?.let { message ->
            Column(
                Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlatTextButton("重试", filled = true) { PlaybackSession.retry() }
            }
        }

        if (controlsVisible) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0x99000000), Color(0x11000000), Color(0xA6000000)),
                    ),
                ),
            )
            if (!inline) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconAction(PlayerGlyph.Back, 40.dp) { onBack() }
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = PlaybackSession.title.ifBlank { "正在播放" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(PlayerGlyph.More, 40.dp) { showMore = true }
                }
            }

            Row(
                Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!inline) {
                    IconAction(PlayerGlyph.Previous, 40.dp, enabled = onPrevious != null) {
                        onPrevious?.invoke()
                    }
                }
                Box(
                    Modifier
                        .size(if (inline) 48.dp else 62.dp)
                        .focusRequester(playFocus)
                        .focusable()
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.94f))
                        .clickable { PlaybackSession.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerIcon(
                        glyph = if (PlaybackSession.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play,
                        tint = Color.Black,
                        modifier = Modifier.size(if (inline) 22.dp else 28.dp),
                    )
                }
                if (!inline) {
                    IconAction(PlayerGlyph.Next, 40.dp, enabled = onNext != null) {
                        onNext?.invoke()
                    }
                }
            }

            if (showSpeedMenu) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = if (inline) 66.dp else 92.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        FlatTextButton(
                            text = if (value == 1f) "1.0x" else "${value}x",
                            filled = PlaybackSession.speed == value,
                        ) { PlaybackSession.updateSpeed(value) }
                    }
                }
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                val duration = PlaybackSession.durationMs.coerceAtLeast(1L)
                Slider(
                    value = position.coerceIn(0L, duration).toFloat(),
                    onValueChange = {
                        dragging = true
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        PlaybackSession.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..duration.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(22.dp),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatTime(position),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = " / ${formatTime(PlaybackSession.durationMs)}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    FlatTextButton(
                        text = if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) "循环 1" else "循环",
                    ) { PlaybackSession.toggleRepeat() }
                    FlatTextButton(text = "${PlaybackSession.speed}x") { showSpeedMenu = !showSpeedMenu }
                    if (!inline && PlaybackSession.queue.size > 1) {
                        IconAction(PlayerGlyph.Episodes, 34.dp) { showEpisodes = true }
                    }
                    IconAction(
                        glyph = if (inline) PlayerGlyph.Fullscreen else PlayerGlyph.ExitFullscreen,
                        size = 36.dp,
                    ) { onToggleFullscreen() }
                }
            }
        }

        if (showEpisodes) {
            OverlayPanel(onDismiss = { showEpisodes = false }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "选集 · 共 ${PlaybackSession.queue.size} 集",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    FlatTextButton("关闭") { showEpisodes = false }
                }
                Spacer(Modifier.height(10.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(78.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                ) {
                    itemsIndexed(PlaybackSession.queue) { index, episode ->
                        val selected = index == PlaybackSession.currentIndex
                        FlatTextButton(
                            text = episode.name.ifBlank { "${index + 1}" },
                            filled = selected,
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
                inline = inline,
                danmakuOn = danmakuOn,
                onDismiss = { showMore = false },
                onPrevious = onPrevious,
                onNext = onNext,
                onEpisodes = {
                    showMore = false
                    showEpisodes = true
                },
                onSpeed = {
                    showMore = false
                    showSpeedMenu = true
                },
                onToggleDanmaku = { danmakuOn = !danmakuOn },
                onFullscreen = onToggleFullscreen,
            )
        }
    }
}

/** 详情页顶部小窗播放器。双击或点全屏图标进入全屏路由。 */
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

@Composable
private fun MorePanel(
    inline: Boolean,
    danmakuOn: Boolean,
    onDismiss: () -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onEpisodes: () -> Unit,
    onSpeed: () -> Unit,
    onToggleDanmaku: () -> Unit,
    onFullscreen: () -> Unit,
) {
    OverlayPanel(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("播放设置", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            FlatTextButton("关闭", onClick = onDismiss)
        }
        Spacer(Modifier.height(8.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        ) {
            item {
                FlatTextButton("上集", enabled = onPrevious != null) { onPrevious?.invoke(); onDismiss() }
            }
            item {
                FlatTextButton("下集", enabled = onNext != null) { onNext?.invoke(); onDismiss() }
            }
            item { FlatTextButton("重播") { PlaybackSession.replay(); onDismiss() } }
            item { FlatTextButton("刷新") { PlaybackSession.refresh(); onDismiss() } }
            item { FlatTextButton("选集", enabled = PlaybackSession.queue.size > 1, onClick = onEpisodes) }
            item { FlatTextButton("倍速 ${PlaybackSession.speed}x", onClick = onSpeed) }
            item {
                FlatTextButton(
                    text = if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) "循环 开" else "循环 关",
                ) { PlaybackSession.toggleRepeat() }
            }
            item { FlatTextButton(if (danmakuOn) "弹幕 开" else "弹幕 关", onClick = onToggleDanmaku) }
            item { FlatTextButton("解码 硬解") {} }
            item { FlatTextButton("渲染 Surface") {} }
            item { FlatTextButton("字幕 默认") {} }
            item { FlatTextButton("音轨 默认") {} }
            item { FlatTextButton("片头 00:00") {} }
            item { FlatTextButton("片尾 00:00") {} }
            item { FlatTextButton("画面 适应") {} }
            item { FlatTextButton(if (inline) "全屏" else "退出全屏", onClick = onFullscreen) }
        }
    }
}

@Composable
private fun OverlayPanel(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xD9000000)).clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF21C1C1E))
                .clickable(enabled = false) {}
                .padding(16.dp),
        ) {
            content()
        }
    }
}

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
            .background(if (focused) Color.White.copy(alpha = 0.18f) else Color.Transparent)
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
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
private fun FlatTextButton(
    text: String,
    filled: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                when {
                    filled -> Color.White
                    focused -> Color.White.copy(alpha = 0.18f)
                    else -> Color.White.copy(alpha = 0.08f)
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> Color.White.copy(alpha = 0.3f)
                filled -> Color.Black
                else -> Color.White
            },
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp,
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
