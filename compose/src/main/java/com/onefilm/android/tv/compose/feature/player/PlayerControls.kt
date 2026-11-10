package com.onefilm.android.tv.compose.feature.player


import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.net.TrafficStats
import android.os.Build
import android.os.Process
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.focusProperties
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.danmaku.DanmakuView
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DlnaDevice
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot
import com.onefilm.android.tv.compose.design.component.AppLogoLoading
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全屏播放器的按键状态。Activity 在控件隐藏时直接消费确认/左右键，
 * 避免控件移除后焦点丢失导致按键到不了 Compose。
 */
object TvPlayerKeyState {
    @Volatile var fullscreenActive: Boolean = false
    @Volatile var controlsVisible: Boolean = true
    @Volatile var playFocused: Boolean = false
    @Volatile var menuOpen: Boolean = false
    @Volatile var requestControls: (() -> Unit)? = null
    @Volatile var requestControlsToMenu: (() -> Unit)? = null
    @Volatile var hideControls: (() -> Unit)? = null
    @Volatile var closeOverlay: (() -> Boolean)? = null
    @Volatile var seekBy: ((Long) -> Unit)? = null
    @Volatile var onUserInteraction: (() -> Unit)? = null
}


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
    LaunchedEffect(danmakuKey, player) {
        if (player != null) {
            DanmakuController.ensureLoaded(context, danmakuKey, ComposeDependencies.danmakuDataSource)
        }
    }
    if (legacy == null || player == null) {
        Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.player_empty), color = Ink)
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
                DanmakuController.attach(danmaku, video, player, danmakuKey)
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
                if (danmaku != null) {
                    if (!DanmakuController.attachedTo(danmaku)) {
                        DanmakuController.attach(danmaku, video, PlaybackSession.player, danmakuKey)
                    } else {
                        DanmakuController.setPlayer(PlaybackSession.player)
                    }
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
    host: PlayerChromeHost? = null,
    modifier: Modifier = Modifier,
) {
    val player = PlaybackSession.player
    val context = LocalContext.current
    val useFocus = host?.usesFocus == true
    val danmakuKey = "${PlaybackSession.siteKey}:${PlaybackSession.vodId}:${PlaybackSession.currentEpisode?.url.orEmpty()}"
    val configuration = LocalConfiguration.current
    val isTv = (configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    var controlsVisible by remember { mutableStateOf(!inline) }
    var position by remember { mutableLongStateOf(PlaybackSession.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var showEpisodes by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showDlna by remember { mutableStateOf(false) }
    var showParse by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var showDanmakuPanel by remember { mutableStateOf(false) }
    var danmakuSnapshot by remember { mutableStateOf<SettingsSnapshot?>(null) }
    /** -1=根页，-2=屏蔽词编辑，>=0=选项页。 */
    var danmakuPage by remember { mutableIntStateOf(-1) }
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }
    var showEditions by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var subtitleLoading by remember { mutableStateOf(false) }
    var subtitleList by remember {
        mutableStateOf<List<com.onefilm.android.tv.compose.bridge.SubtitleCandidate>>(emptyList())
    }
    var trackList by remember { mutableStateOf<List<com.onefilm.android.tv.compose.bridge.LegacyTrack>>(emptyList()) }
    var parseHint by remember { mutableStateOf<String?>(null) }
    // 解析引擎列表来自配置，配置变更后重新进入播放页即可刷新。
    val parseEngines = remember {
        runCatching { ComposeDependencies.parseEngine.engines() }.getOrDefault(emptyList())
    }
    var dlnaDevices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var dlnaLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var overflowEntries by remember { mutableStateOf<List<PlayerMenuEntry>>(emptyList()) }
    /** 当前子面板是从「更多」打开的：返回键关闭子面板后要回到「更多」。 */
    var moreParent by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var playButtonFocused by remember { mutableStateOf(false) }
    // 控制栏隐藏时左右键只快进/快退：用一条短提示给出位置反馈，不唤出控制栏。
    var seekHint by remember { mutableStateOf<String?>(null) }
    val playFocus = remember { FocusRequester() }
    val fullscreenFocus = remember { FocusRequester() }
    val menuFirstFocus = remember { FocusRequester() }
    val episodesFirstFocus = remember { FocusRequester() }
    val parseFirstFocus = remember { FocusRequester() }
    val qualityFirstFocus = remember { FocusRequester() }
    val tracksFirstFocus = remember { FocusRequester() }
    val danmakuFirstFocus = remember { FocusRequester() }
    val subtitleFirstFocus = remember { FocusRequester() }
    val chaptersFirstFocus = remember { FocusRequester() }
    val editionsFirstFocus = remember { FocusRequester() }
    val descriptionFirstFocus = remember { FocusRequester() }
    val speedFirstFocus = remember { FocusRequester() }
    val moreFirstFocus = remember { FocusRequester() }
    // 面板打开时把焦点移进去，关闭后还给菜单行；否则遥控器焦点会留在已移除的节点上。
    var panelWasOpen by remember { mutableStateOf(false) }
    // 任何按键/点击都算一次操作，控制栏只在 10 秒无操作后才隐藏。
    var lastInteractionAt by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    // 用上下键唤出控制栏时，焦点直接落到菜单行第一项。
    var revealToMenu by remember { mutableStateOf(false) }
    // 任一弹层打开时，底层播放器控件不再参与焦点遍历（避免焦点跑到左上/中间/右上）。
    val overlayOpen = showEpisodes || showMore || showDlna || showParse || showQuality ||
        showTracks || showDanmakuPanel || showSubtitlePanel || showChapters ||
        showEditions || showDescription || showSpeed

    val resContext = LocalContext.current

    // 本地字幕：用应用内文件浏览页（电视上没有 DocumentsUI，OpenDocument 会崩）。
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "subtitle"
            PlaybackSession.addSubtitle(name, uri.toString())
        }
    }

    fun seekWithHint(deltaMs: Long) {
        PlaybackSession.seekBy(deltaMs)
        val target = (PlaybackSession.positionMs() + deltaMs).coerceAtLeast(0L)
        val action = resContext.getString(
            if (deltaMs > 0) R.string.player_forward else R.string.player_rewind
        )
        seekHint = resContext.getString(
            R.string.player_seek_hint,
            action,
            kotlin.math.abs(deltaMs) / 1000,
            formatTime(target),
        )
    }

    fun closeTopOverlay(): Boolean {
        // 从「更多」打开的子面板关闭后，返回上一层「更多」，再按一次才真正收起。
        fun restoreMore() {
            if (moreParent) {
                moreParent = false
                showMore = true
            }
        }
        return when {
            showDlna -> { showDlna = false; restoreMore(); true }
            showParse -> { showParse = false; restoreMore(); true }
            showQuality -> { showQuality = false; restoreMore(); true }
            showTracks -> { showTracks = false; restoreMore(); true }
            showDanmakuPanel -> {
                if (danmakuPage != -1) {
                    danmakuPage = -1
                } else {
                    showDanmakuPanel = false
                    restoreMore()
                }
                true
            }
            showSubtitlePanel -> { showSubtitlePanel = false; restoreMore(); true }
            showChapters -> { showChapters = false; restoreMore(); true }
            showEditions -> { showEditions = false; restoreMore(); true }
            showDescription -> { showDescription = false; restoreMore(); true }
            showSpeed -> { showSpeed = false; restoreMore(); true }
            showEpisodes -> { showEpisodes = false; restoreMore(); true }
            showMore -> { showMore = false; moreParent = false; true }
            else -> false
        }
    }

    // 触摸端：返回键关闭最上层图层；TV 端由 Activity 的 dispatchKeyEvent 先行处理。
    BackHandler(enabled = !inline) {
        if (!closeTopOverlay()) onBack?.invoke()
    }

    SideEffect {
        host?.onControlsVisible(controlsVisible)
        host?.onMenuOpen(overlayOpen)
        host?.bind(
            reveal = {
                if (!controlsVisible) revealToMenu = false
                controlsVisible = true
            },
            revealToMenu = {
                if (!controlsVisible) revealToMenu = true
                controlsVisible = true
            },
            hide = { controlsVisible = false },
            closeTop = { closeTopOverlay() },
            seekBy = { deltaMs -> seekWithHint(deltaMs) },
            userInteraction = { lastInteractionAt = SystemClock.uptimeMillis() },
        )
    }
    LaunchedEffect(seekHint) {
        if (seekHint != null) {
            delay(1_200)
            seekHint = null
        }
    }
    LaunchedEffect(parseHint) {
        if (parseHint != null) {
            delay(2_000)
            parseHint = null
        }
    }

    fun startParse(engine: String?) {
        scope.launch {
            val ok = PlaybackSession.parseAndPlay(engine)
            if (!ok) parseHint = resContext.getString(R.string.player_parse_failed)
        }
    }

    fun reloadDanmaku() {
        danmakuSnapshot = runCatching { ComposeDependencies.settingsDataSource.load() }.getOrNull()
        DanmakuController.refreshSettings()
    }

    fun toggleDanmaku(next: Boolean) {
        DanmakuController.updateEnabled(next)
        PlaybackSession.legacyPlayer?.setDanmakuEnabled(next)
        if (next) {
            DanmakuController.ensureLoaded(context, danmakuKey, ComposeDependencies.danmakuDataSource)
        }
        reloadDanmaku()
    }

    /** 从「更多」打开子面板：先隐藏更多面板，返回时再恢复。 */
    fun openFromMore() {
        if (showMore) {
            moreParent = true
            showMore = false
        }
    }

    val menuEntries = buildPlayerMenu(
        PlayerMenuActions(
            onPrevious = onPrevious,
            onNext = onNext,
            onOpenEpisodes = { openFromMore(); showEpisodes = true },
            onOpenChapters = if (PlaybackSession.chapters.isNotEmpty()) ({ openFromMore(); showChapters = true }) else null,
            onOpenEditions = if (PlaybackSession.editions.isNotEmpty()) ({ openFromMore(); showEditions = true }) else null,
            onOpenDescription = if (PlaybackSession.description.isNotBlank()) ({ openFromMore(); showDescription = true }) else null,
            onToggleFavorite = { PlaybackSession.toggleFavorite() },
            onParse = if (parseEngines.isEmpty()) null else ({ openFromMore(); showParse = true }),
            onOpenQuality = if (PlaybackSession.qualityOptions.size > 1) ({ openFromMore(); showQuality = true }) else null,
            onSwitchLine = if (PlaybackSession.hasAlternatives) ({ PlaybackSession.switchLine() }) else null,
            onOpenTracks = ({
                openFromMore()
                trackList = PlaybackSession.tracks()
                showTracks = true
            }),
            onOpenDanmaku = ({
                openFromMore()
                danmakuPage = -1
                danmakuSnapshot = runCatching { ComposeDependencies.settingsDataSource.load() }.getOrNull()
                showDanmakuPanel = true
            }),
            onOpenSpeed = ({ openFromMore(); showSpeed = true }),
            onSearchSubtitle = if (runCatching { ComposeDependencies.subtitleRepository.available }.getOrDefault(false)) ({
                openFromMore()
                subtitleLoading = true
                showSubtitlePanel = true
                subtitleList = emptyList()
                scope.launch {
                    val repo = ComposeDependencies.subtitleRepository
                    val keyword = com.onefilm.android.tv.core.danmaku.DanmakuQuery.bestName(PlaybackSession.title)
                    subtitleList = runCatching { repo.search(keyword) }.getOrDefault(emptyList())
                    subtitleLoading = false
                }
            }) else null,
            onImportSubtitle = ({
                openFromMore()
                val intent = runCatching { ComposeDependencies.fileLauncher?.intent(context) }.getOrNull()
                if (intent != null) runCatching { subtitlePicker.launch(intent) }
            }),
            onPip = {
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
            },
            onDlna = {
                openFromMore()
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
            },
        ),
        resContext,
    )

    LaunchedEffect(player, controlsVisible, PlaybackSession.isPlaying, showEpisodes, showMore, showDlna, showParse, showQuality, showTracks, showDanmakuPanel, showSubtitlePanel, showChapters, showEditions, showDescription, showSpeed) {
        if (!controlsVisible) return@LaunchedEffect
        // 菜单打开或暂停时不自动隐藏；播放中则 10 秒无操作才隐藏。
        if (!PlaybackSession.isPlaying || overlayOpen) return@LaunchedEffect
        // 重新开始计时：暂停后恢复、弹窗关闭都要从当下重新数 10 秒。
        lastInteractionAt = SystemClock.uptimeMillis()
        while (true) {
            if (SystemClock.uptimeMillis() - lastInteractionAt >= 10_000L) {
                controlsVisible = false
                break
            }
            delay(250)
        }
    }
    // 不要再在“暂停时自动显示控制栏”：seek 引起的瞬时 isPlaying=false 会把
    // 控制栏弹出来，导致隐藏态按左右键既快进又显示控制栏。用户主动暂停时，
    // 按键处理本身就会唤出控制栏。
    // 面板打开时把焦点移进去，关闭后还给菜单行；否则遥控器焦点会留在已移除的节点上。
    LaunchedEffect(showEpisodes, showParse, showQuality, showTracks, showDanmakuPanel, showSubtitlePanel, showChapters, showEditions, showDescription, showSpeed) {
        if (!useFocus) return@LaunchedEffect
        val open = overlayOpen
        if (open) {
            panelWasOpen = true
            delay(80)
            val first = when {
                showSpeed -> speedFirstFocus
                showDescription -> descriptionFirstFocus
                showChapters -> chaptersFirstFocus
                showEditions -> editionsFirstFocus
                showSubtitlePanel -> subtitleFirstFocus
                showTracks -> tracksFirstFocus
                showDanmakuPanel -> danmakuFirstFocus
                showQuality -> qualityFirstFocus
                showParse -> parseFirstFocus
                else -> episodesFirstFocus
            }
            runCatching { first.requestFocus() }
        } else if (panelWasOpen) {
            panelWasOpen = false
            delay(80)
            runCatching { menuFirstFocus.requestFocus() }
        }
    }
    // 弹幕面板有根页/选项页/屏蔽词页三级，翻页后要重新把焦点放到第一项。
    LaunchedEffect(showDanmakuPanel, danmakuPage) {
        if (!useFocus) return@LaunchedEffect
        if (showDanmakuPanel) {
            delay(80)
            runCatching { danmakuFirstFocus.requestFocus() }
        }
    }
    // 「更多」是从菜单行点开的，必须显式把焦点送进面板的第一项。
    // 首帧可能还没完成布局，requestFocus 会抛异常，这里短间隔重试两次。
    LaunchedEffect(showMore) {
        if (!useFocus) return@LaunchedEffect
        if (showMore) {
            delay(120)
            if (runCatching { moreFirstFocus.requestFocus() }.isFailure) {
                delay(120)
                runCatching { moreFirstFocus.requestFocus() }
            }
        }
    }
    LaunchedEffect(showMore, isTv, inline) {
        if (!useFocus) return@LaunchedEffect
        // The inline player must not steal focus from the detail actions.
        // Only the dedicated full-screen route owns the initial player focus.
        if (!inline && !showMore && isTv) {
            runCatching { playFocus.requestFocus() }
        }
    }
    LaunchedEffect(player, controlsVisible, isTv, inline) {
        if (!useFocus) return@LaunchedEffect
        if (!inline && controlsVisible && isTv) {
            // 等控件完成淡入后再请求焦点，避免暂停后确认键落空。
            delay(100)
            val target = if (revealToMenu) menuFirstFocus else playFocus
            runCatching { target.requestFocus() }
            revealToMenu = false
        }
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
                        lastInteractionAt = SystemClock.uptimeMillis()
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
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppLogoLoading(size = 64.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.player_loading_hint),
                    color = Ink,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                // 缓冲时显示实时下载速度，与旧播放器中心加载区一致
                TrafficText()
            }
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
                TextAction(stringResource(R.string.detail_retry)) { PlaybackSession.retry() }
            }
        }

        if (controlsVisible && !overlayOpen) {
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
                        text = buildString {
                            append(PlaybackSession.title.ifBlank { stringResource(R.string.player_playing) })
                            val episodeName = PlaybackSession.currentEpisode?.name.orEmpty()
                            if (episodeName.isNotBlank()) {
                                append("  ")
                                append(episodeName)
                            }
                        },
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // 右上角时钟：与旧播放器一致，仅在控制栏可见时刷新
                    PlayerClock()
                    Spacer(Modifier.width(8.dp))
                    IconAction(PlayerGlyph.More, 42.dp) {
                        overflowEntries = menuEntries
                        moreParent = false
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
                        // 全屏下键固定落到菜单行，避免跑到底部右侧的全屏按钮。
                        .then(
                            if (inline) Modifier
                            else Modifier.focusProperties { down = menuFirstFocus },
                        )
                        .focusRequester(playFocus)
                        .onFocusChanged {
                            playButtonFocused = it.isFocused
                            host?.onPlayFocused(it.isFocused)
                        }
                        .focusable()
                        .clip(CircleShape)
                        .background(
                            if (playButtonFocused) Press else Color.Black.copy(alpha = 0.32f),
                        )
                        .clickable { PlaybackSession.toggle() },
                    contentAlignment = Alignment.Center,
                ) {
                    PlayerIcon(
                        glyph = if (PlaybackSession.isPlaying) PlayerGlyph.Pause else PlayerGlyph.Play,
                        tint = if (playButtonFocused) Color.Black else Ink,
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
                        moreParent = false
                        showMore = true
                    },
                    onFullscreen = onToggleFullscreen,
                    fullscreenFocusRequester = if (inline) fullscreenFocus else null,
                    firstItemFocusRequester = if (inline) null else menuFirstFocus,
                )
            }
        }

        if (showEpisodes) {
            PanelScrim(onDismiss = { showEpisodes = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.queue) { index, episode ->
                        val selected = index == PlaybackSession.currentIndex
                        FlatTile(
                            text = episode.name.ifBlank { "${index + 1}" },
                            emphasis = selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(episodesFirstFocus) else Modifier),
                        ) {
                            showEpisodes = false
                            onSelectEpisode?.invoke(index)
                        }
                    }
                }
            }
        }

        if (showParse) {
            PanelScrim(onDismiss = { showParse = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    item {
                        FlatTile(
                            text = stringResource(R.string.player_parse_default),
                            modifier = Modifier.fillMaxWidth().focusRequester(parseFirstFocus),
                        ) { startParse(null) }
                    }
                    items(parseEngines) { entry ->
                        FlatTile(
                            text = entry.name,
                            emphasis = entry.god,
                            modifier = Modifier.fillMaxWidth(),
                        ) { startParse(entry.name) }
                    }
                }
            }
        }

        if (showQuality) {
            PanelScrim(onDismiss = { showQuality = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.qualityOptions) { index, option ->
                        FlatTile(
                            text = option.name.ifBlank { "${index + 1}" },
                            emphasis = index == PlaybackSession.qualityIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(qualityFirstFocus) else Modifier),
                        ) {
                            PlaybackSession.selectQuality(index)
                        }
                    }
                }
            }
        }

        if (showTracks) {
            PanelScrim(onDismiss = { showTracks = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(trackList) { index, track ->
                        FlatTile(
                            text = trackLabel(track),
                            emphasis = track.selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(tracksFirstFocus) else Modifier),
                        ) {
                            PlaybackSession.selectTrack(track)
                            trackList = PlaybackSession.tracks()
                        }
                    }
                }
            }
        }

        if (showDanmakuPanel) {
            PanelScrim(onDismiss = {
                showDanmakuPanel = false
                danmakuPage = -1
            }) {
                val snapshot = danmakuSnapshot
                when {
                    snapshot == null -> Text(
                        text = stringResource(R.string.player_loading_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )

                    danmakuPage == -2 -> DanmakuTextPanel(
                        initial = snapshot.danmakuBlockWords,
                        firstFocus = danmakuFirstFocus,
                        onSave = { words ->
                            ComposeDependencies.settingsDataSource.setDanmakuBlockWords(words)
                            reloadDanmaku()
                            danmakuPage = -1
                        },
                    )

                    else -> {
                        val items = buildDanmakuItems(snapshot, onToggleShow = { toggleDanmaku(it) }) { reloadDanmaku() }
                        DanmakuItemGrid(
                            items = items,
                            firstFocus = danmakuFirstFocus,
                            onActivate = { index ->
                                val item = items[index]
                                if (item.text) danmakuPage = -2 else item.onClick()
                            },
                        )
                    }
                }
            }
        }

        if (showSpeed) {
            PanelScrim(onDismiss = { showSpeed = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.speedPresets) { index, value ->
                        FlatTile(
                            text = PlaybackSession.speedText(value),
                            emphasis = kotlin.math.abs(value - PlaybackSession.speed) < 0.011f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(speedFirstFocus) else Modifier),
                        ) { PlaybackSession.updateSpeed(value) }
                    }
                }
            }
        }

        if (showSubtitlePanel) {
            PanelScrim(onDismiss = { showSubtitlePanel = false }) {
                when {
                    subtitleLoading -> Text(
                        text = stringResource(R.string.player_subtitle_searching),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )

                    subtitleList.isEmpty() -> Text(
                        text = stringResource(R.string.player_subtitle_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )

                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    ) {
                        itemsIndexed(subtitleList) { index, item ->
                            FlatTile(
                                text = item.name.ifBlank { item.lang.ifBlank { "Subtitle" } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (index == 0) Modifier.focusRequester(subtitleFirstFocus) else Modifier),
                            ) {
                                scope.launch {
                                    subtitleLoading = true
                                    val repo = ComposeDependencies.subtitleRepository
                                    val files = runCatching { repo.files(item.id) }.getOrDefault(emptyList())
                                    val target = files.firstOrNull { it.url.isNotBlank() }
                                    val path = if (target != null) {
                                        runCatching { repo.download(target) }.getOrNull()
                                    } else {
                                        null
                                    }
                                    subtitleLoading = false
                                    if (!path.isNullOrBlank() && target != null) {
                                        PlaybackSession.addSubtitle(target.name, path, target.lang)
                                        showSubtitlePanel = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showChapters) {
            PanelScrim(onDismiss = { showChapters = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.chapters) { index, chapter ->
                        FlatTile(
                            text = if (chapter.timeMs >= 0) {
                                "${chapter.label} [${formatTime(chapter.timeMs)}]"
                            } else {
                                chapter.label
                            },
                            emphasis = chapter.selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(chaptersFirstFocus) else Modifier),
                        ) { PlaybackSession.selectChapter(index) }
                    }
                }
            }
        }

        if (showEditions) {
            PanelScrim(onDismiss = { showEditions = false }) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                ) {
                    itemsIndexed(PlaybackSession.editions) { index, edition ->
                        FlatTile(
                            text = if (edition.durationMs >= 0) {
                                "${edition.label} [${formatTime(edition.durationMs)}]"
                            } else {
                                edition.label
                            },
                            emphasis = edition.selected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(editionsFirstFocus) else Modifier),
                        ) { PlaybackSession.selectEdition(index) }
                    }
                }
            }
        }

        if (showDescription) {
            PanelScrim(onDismiss = { showDescription = false }) {
                val scrollState = rememberScrollState()
                Text(
                    text = PlaybackSession.description,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .focusRequester(descriptionFirstFocus)
                        .focusable()
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val delta = when (event.key) {
                                Key.DirectionDown -> 120f
                                Key.DirectionUp -> -120f
                                else -> return@onPreviewKeyEvent false
                            }
                            scrollState.dispatchRawDelta(delta)
                            true
                        }
                        .verticalScroll(scrollState)
                        .padding(4.dp),
                )
            }
        }

        // 解析进行中/失败的提示，与快进提示区分位置，避免互相遮挡。
        val parseCapsule = if (PlaybackSession.parsing) {
            stringResource(R.string.player_parsing)
        } else {
            parseHint
        }
        parseCapsule?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (inline) 130.dp else 190.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = hint,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
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
                firstFocus = moreFirstFocus,
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

        // 控制栏隐藏时的快进/快退提示：扁平胶囊，1.2 秒后自动消失。
        seekHint?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (inline) 90.dp else 150.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = hint,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** 轨道面板行文案：视频轨 / 音轨 / 字幕 + 名称。 */
@Composable
private fun trackLabel(track: com.onefilm.android.tv.compose.bridge.LegacyTrack): String {
    val name = track.name.ifBlank { track.format }
    return when (track.type) {
        C.TRACK_TYPE_VIDEO -> stringResource(R.string.player_video_track, name)
        C.TRACK_TYPE_AUDIO -> stringResource(R.string.player_audio_track, name)
        C.TRACK_TYPE_TEXT -> stringResource(R.string.player_subtitle, name)
        else -> name
    }
}

/** 详情页顶部小窗播放器。 */
@Composable
fun InlinePlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit = { VideoSurface(Modifier.fillMaxSize()) },
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .border(
                2.dp,
                if (focused) Color.White else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter)
                ) {
                    onExpand()
                    true
                } else false
            },
    ) {
        video()
        PlayerControls(
            inline = true,
            onToggleFullscreen = onExpand,
            modifier = Modifier.fillMaxSize(),
        )
        if (focused) {
            Text(
                text = stringResource(R.string.player_confirm_fullscreen),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** 自定义细进度条：白色 2dp 轨道 + 白色圆点，不依赖 Material Slider。 */
/** 进度条下方的紧凑菜单项：自适应宽度，保证手机可点按。 */
/** 纯图标按钮：无边框、无底色，仅焦点态显示极淡白色。 */
/** 文本按钮：扁平、无边框，选中态才用白色底。 */
/** 右上角时钟：HH:mm:ss，每秒刷新；控制栏隐藏时 composable 被移除，自动停止。 */
@Composable
private fun PlayerClock() {
    val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    var text by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            text = formatter.format(Date())
        }
    }
    Text(
        text = text,
        color = InkDim,
        fontSize = 14.sp,
        maxLines = 1,
    )
}

/** 缓冲时的实时下载速度：KB/s，超过 1024 KB/s 显示 MB/s。 */
@Composable
private fun TrafficText() {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        var lastBytes = TrafficStats.getUidRxBytes(Process.myUid())
        while (true) {
            delay(1_000)
            val current = TrafficStats.getUidRxBytes(Process.myUid())
            if (current == TrafficStats.UNSUPPORTED.toLong() || current < 0L ||
                lastBytes == TrafficStats.UNSUPPORTED.toLong() || lastBytes < 0L
            ) {
                text = ""
                continue
            }
            val kb = ((current - lastBytes).coerceAtLeast(0L)) / 1024f
            lastBytes = current
            text = if (kb < 1024f) "${kb.toInt()} KB/s" else "%.1f MB/s".format(kb / 1024f)
        }
    }
    if (text.isNotBlank()) {
        Text(text = text, color = InkFaint, fontSize = 11.sp, maxLines = 1)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
