package com.onefilm.android.tv.compose.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

/** 视频画面：绑定旧 PlayerManager，Exo 用 PlayerView，MPV 额外挂 SurfaceView。 */
@Composable
fun VideoSurface(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val legacy = PlaybackSession.legacyPlayer
    val player = PlaybackSession.player
    val textureRender = PlaybackSession.textureRender
    val resizeMode = PlaybackSession.resizeMode
    val mpv = PlaybackSession.engineMpv
    val episode = PlaybackSession.currentEpisode
    val danmakuKey = "${PlaybackSession.siteKey}:${PlaybackSession.vodId}:${episode?.url.orEmpty()}"
    LaunchedEffect(player) {
        while (player != null) {
            DanmakuController.setClock(player.currentPosition)
            delay(50)
        }
    }
    LaunchedEffect(danmakuKey, player) {
        if (player != null) {
            DanmakuController.ensureLoaded(context, danmakuKey, ComposeDependencies.danmakuDataSource)
        }
    }
    if (legacy == null || player == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text("暂无播放内容", color = Ink)
        }
        return
    }
    key(mpv, textureRender) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                val root = FrameLayout(ctx)
                val video = PlayerView(ctx).apply {
                    useController = false
                    setRender(if (textureRender) 1 else 0)
                    this.resizeMode = resizeMode
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
                root.addView(video, FrameLayout.LayoutParams(-1, -1))
                if (mpv) {
                    val surfaceView = SurfaceView(ctx)
                    surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            legacy.attachMpvSurface(holder.surface)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            legacy.attachMpvSurface(null)
                        }
                    })
                    root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
                }
                val danmaku = DanmakuView(ctx)
                root.addView(danmaku, FrameLayout.LayoutParams(-1, -1))
                legacy.bindPlayerView(video)
                video.player = player
                DanmakuController.attach(danmaku)
                root.tag = Triple(video, danmaku, mpv)
                root
            },
            update = { root ->
                val triple = root.tag as? Triple<*, *, *>
                val video = triple?.first as? PlayerView
                val danmaku = triple?.second as? DanmakuView
                video?.player = PlaybackSession.player
                video?.resizeMode = PlaybackSession.resizeMode
                video?.setRender(if (PlaybackSession.textureRender) 1 else 0)
                if (danmaku != null && !DanmakuController.attachedTo(danmaku)) {
                    DanmakuController.attach(danmaku)
                }
            },
            onRelease = { root ->
                val triple = root.tag as? Triple<*, *, *>
                (triple?.first as? PlayerView)?.player = null
                (triple?.second as? DanmakuView)?.let { DanmakuController.detach(it) }
                legacy.attachMpvSurface(null)
            },
        )
    }
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
    val player = PlaybackSession.player
    val context = LocalContext.current
    val danmakuKey = "${PlaybackSession.siteKey}:${PlaybackSession.vodId}:${PlaybackSession.currentEpisode?.url.orEmpty()}"
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(PlaybackSession.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showDlna by remember { mutableStateOf(false) }
    var dlnaDevices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var dlnaLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
        add(MenuEntry("片头 ${formatTime(PlaybackSession.openingMs)}") { PlaybackSession.markOpening() })
        add(MenuEntry("片尾 ${formatTime(PlaybackSession.endingMs)}") { PlaybackSession.markEnding() })
        add(MenuEntry("${PlaybackSession.speed}x") { PlaybackSession.cycleSpeed() })
        add(
            MenuEntry(
                if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) "循环 开" else "循环 关",
            ) { PlaybackSession.toggleRepeat() },
        )
        add(MenuEntry(if (danmakuOn) "弹幕 开" else "弹幕 关") {
            danmakuOn = !danmakuOn
            DanmakuController.updateEnabled(danmakuOn)
            PlaybackSession.legacyPlayer?.setDanmakuEnabled(danmakuOn)
            if (danmakuOn) {
                DanmakuController.ensureLoaded(
                    context,
                    danmakuKey,
                    ComposeDependencies.danmakuDataSource,
                )
            }
        })
        add(MenuEntry("画中画") {
            val activity = context.findActivity()
            if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                runCatching {
                    activity.enterPictureInPictureMode(
                        PictureInPictureParams.Builder()
                            .setAspectRatio(Rational(16, 9))
                            .build(),
                    )
                }
            }
        })
        add(MenuEntry("投屏") {
            val source = ComposeDependencies.dlnaDataSource
            if (source != null) {
                showDlna = true
                dlnaLoading = true
                dlnaDevices = emptyList()
                scope.launch {
                    dlnaDevices = runCatching { source.devices() }.getOrDefault(emptyList())
                    dlnaLoading = false
                }
            }
        })
        add(MenuEntry("片尾 ${formatTime(PlaybackSession.endingMs)}") { PlaybackSession.markEnding() })
        add(MenuEntry("画面 ${PlaybackSession.resizeLabel}") { PlaybackSession.cycleResizeMode() })
        add(MenuEntry("引擎 ${if (PlaybackSession.engineMpv) "MPV" else "Exo"}") {
            PlaybackSession.setEngine(!PlaybackSession.engineMpv)
        })
        add(MenuEntry("解码 ${PlaybackSession.decodeLabel}") { PlaybackSession.toggleDecode() })
        add(MenuEntry("渲染 ${PlaybackSession.renderLabel}") { PlaybackSession.toggleRenderMode() })
        add(MenuEntry("字幕 ${PlaybackSession.subtitleLabel}") { PlaybackSession.cycleSubtitle() })
        add(MenuEntry("字幕大小 %.1fx".format(PlaybackSession.subtitleScale)) {
            PlaybackSession.cycleSubtitleScale()
        })
        add(MenuEntry("字幕位置 ${PlaybackSession.subtitlePosition.toInt()}") {
            PlaybackSession.cycleSubtitlePosition()
        })
        add(MenuEntry("字幕颜色 ${PlaybackSession.subtitleColor}") {
            PlaybackSession.cycleSubtitleColor()
        })
        add(MenuEntry("音轨 ${PlaybackSession.audioLabel}") { PlaybackSession.cycleAudio() })
        add(MenuEntry("视轨 ${PlaybackSession.videoLabel}") { PlaybackSession.cycleVideo() })
        add(MenuEntry("字幕偏移 ${PlaybackSession.textOffsetMs}ms") { PlaybackSession.shiftTextOffset(100L) })
        add(MenuEntry("音画偏移 ${PlaybackSession.audioOffsetMs}ms") { PlaybackSession.shiftAudioOffset(100L) })
    }

    LaunchedEffect(player, controlsVisible, PlaybackSession.isPlaying, showEpisodes, showMore, isTv) {
        if (!isTv && controlsVisible && PlaybackSession.isPlaying && !showEpisodes && !showMore) {
            delay(4000)
            controlsVisible = false
        }
    }
    LaunchedEffect(showMore, isTv) {
        if (!showMore && isTv) runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(player, controlsVisible, isTv) {
        if (controlsVisible && isTv) runCatching { playFocus.requestFocus() }
    }
    LaunchedEffect(player) {
        while (player != null) {
            if (!dragging) position = PlaybackSession.positionMs()
            PlaybackSession.enforceMarkers()
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
            AppLogoLoading(
                modifier = Modifier.align(Alignment.Center),
                size = 64.dp,
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

        AnimatedVisibility(
            visible = showMore,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            OverflowPopup(
                entries = overflowEntries,
                inline = inline,
                onDismiss = { showMore = false },
            )
        }

        AnimatedVisibility(
            visible = showDlna,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            DlnaPopup(
                devices = dlnaDevices,
                loading = dlnaLoading,
                onDismiss = { showDlna = false },
                onPick = { device ->
                    val source = ComposeDependencies.dlnaDataSource
                    val url = PlaybackSession.url
                    if (source != null && url != null) {
                        source.cast(
                            deviceId = device.id,
                            title = PlaybackSession.title,
                            url = url,
                            positionMs = PlaybackSession.positionMs(),
                            headers = emptyMap(),
                        )
                        PlaybackSession.player?.pause()
                    }
                    showDlna = false
                },
            )
        }
    }
}

/** 详情页顶部小窗播放器。 */
@Composable
fun InlinePlayer(onExpand: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black).clipToBounds()) {
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
            entries.take(visible).forEachIndexed { index, entry ->
                MenuText(entry, widths[index])
            }
            if (hasOverflow) {
                MenuText(MenuEntry("更多") { onMore(entries.drop(visible)) }, moreWidth)
            }
            Spacer(Modifier.weight(1f))
            IconAction(
                glyph = if (inline) PlayerGlyph.Fullscreen else PlayerGlyph.ExitFullscreen,
                size = fullscreenWidth,
            ) { onFullscreen() }
        }
    }
}

/** 进度条下方的紧凑菜单项：自适应宽度，保证手机可点按。 */
@Composable
private fun MenuText(entry: MenuEntry, width: Dp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
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
            color = if (entry.enabled) Ink.copy(alpha = 0.92f) else InkFaint,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DlnaPopup(
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
private fun OverflowPopup(
    entries: List<MenuEntry>,
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
