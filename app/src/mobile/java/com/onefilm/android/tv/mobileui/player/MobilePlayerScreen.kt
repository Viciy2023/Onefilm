package com.onefilm.android.tv.mobileui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import com.onefilm.android.tv.compose.R as ComposeR
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.DlnaDevice
import com.onefilm.android.tv.compose.bridge.LegacyTrack
import com.onefilm.android.tv.compose.feature.player.DanmakuController
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class MobilePanel {
    Episodes, Quality, Lines, Speed, Danmaku, Subtitles, Tracks, Parse, Chapters, Editions, More,
}

/** 手机全屏播放器：横屏、触摸控制，业务全部复用 PlaybackSession。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobilePlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val scope = rememberCoroutineScope()
    var panel by remember { mutableStateOf<MobilePanel?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(PlaybackSession.positionMs()) }
    var dragging by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var showCast by remember { mutableStateOf(false) }
    var castDevices by remember { mutableStateOf<List<DlnaDevice>>(emptyList()) }
    var castLoading by remember { mutableStateOf(false) }

    val duration = PlaybackSession.durationMs

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "subtitle"
            PlaybackSession.addSubtitle(name, uri.toString())
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!dragging) position = PlaybackSession.positionMs()
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, PlaybackSession.isPlaying, panel, showDescription, showCast) {
        if (controlsVisible && PlaybackSession.isPlaying && panel == null && !showDescription && !showCast) {
            delay(4_000)
            controlsVisible = false
        }
    }
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            PlaybackSession.pause()
        }
    }
    BackHandler {
        when {
            showDescription -> showDescription = false
            showCast -> showCast = false
            panel != null -> panel = null
            else -> onBack()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MobileVideoSurface(Modifier.fillMaxSize())

        // 点空白显隐控制条（放在控件之前，控件会盖在它上面）
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { controlsVisible = !controlsVisible },
        )

        if (PlaybackSession.isBuffering && PlaybackSession.error == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
        }
        PlaybackSession.error?.let { message ->
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 40.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(message, color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(ComposeR.string.detail_retry),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { PlaybackSession.retry() },
                )
            }
        }

        // 顶部：返回 + 标题
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = if (controlsVisible) 0.35f else 0f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                color = Color.White,
                fontSize = 34.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(horizontal = 10.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    PlaybackSession.title.ifBlank { stringResource(ComposeR.string.player_playing) },
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val episode = PlaybackSession.currentEpisode?.name.orEmpty()
                if (episode.isNotBlank()) {
                    Text(
                        episode,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                stringResource(ComposeR.string.common_more),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                    .clickable {
                        controlsVisible = false
                        panel = MobilePanel.More
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (controlsVisible) {
            // 中间播放/暂停
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { PlaybackSession.toggle() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (PlaybackSession.isPlaying) "⏸" else "▶",
                    color = Color.White,
                    fontSize = 26.sp,
                )
            }

            // 底部进度 + 操作
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatTime(position), color = Color.White, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(formatTime(duration), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
                Slider(
                    value = position.coerceIn(0L, duration.coerceAtLeast(1L)).toFloat(),
                    onValueChange = {
                        dragging = true
                        position = it.toLong()
                    },
                    onValueChangeFinished = {
                        PlaybackSession.seekTo(position)
                        dragging = false
                    },
                    valueRange = 0f..(duration.coerceAtLeast(1L)).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    PlayerGlyph("⏮") {
                        PlaybackSession.playPrevious(context, ComposeDependencies.playbackResolver)
                    }
                    PlayerGlyph(if (PlaybackSession.isPlaying) "⏸" else "▶") { PlaybackSession.toggle() }
                    PlayerGlyph("⏭") {
                        PlaybackSession.playNext(context, ComposeDependencies.playbackResolver)
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }

        panel?.let { current ->
            MobilePlayerSheet(
                panel = current,
                onDismiss = { panel = null },
                onOpenPanel = { panel = it },
                onShowDescription = { panel = null; showDescription = true },
                onShowCast = {
                    panel = null
                    showCast = true
                    castLoading = true
                    castDevices = emptyList()
                    scope.launch {
                        castDevices = runCatching {
                            ComposeDependencies.dlnaDataSource?.devices()
                        }.getOrNull().orEmpty()
                        castLoading = false
                    }
                },
                onImportSubtitle = {
                    panel = null
                    runCatching {
                        subtitlePicker.launch(Intent(context, com.onefilm.android.tv.ui.activity.FileActivity::class.java))
                    }
                },
                onPip = {
                    if (activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        runCatching {
                            activity.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build(),
                            )
                        }
                    }
                    panel = null
                },
            )
        }

        if (showDescription) {
            ModalBottomSheet(onDismissRequest = { showDescription = false }) {
                Text(
                    text = PlaybackSession.description.ifBlank { "—" },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }

        if (showCast) {
            ModalBottomSheet(onDismissRequest = { showCast = false }) {
                Text(
                    stringResource(ComposeR.string.player_cast),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
                if (castLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (castDevices.isEmpty()) {
                    Text(
                        stringResource(ComposeR.string.cast_none),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                        itemsIndexed(castDevices) { _, device ->
                            MobileSheetRow(device.name.ifBlank { device.id }) {
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
                                showCast = false
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 播放器面板：底部弹出，触摸选择。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobilePlayerSheet(
    panel: MobilePanel,
    onDismiss: () -> Unit,
    onOpenPanel: (MobilePanel) -> Unit,
    onShowDescription: () -> Unit,
    onShowCast: () -> Unit,
    onImportSubtitle: () -> Unit,
    onPip: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engines = remember { runCatching { ComposeDependencies.parseEngine.engines() }.getOrDefault(emptyList()) }
    val title = when (panel) {
        MobilePanel.Episodes -> stringResource(ComposeR.string.player_select_episode)
        MobilePanel.Quality -> stringResource(ComposeR.string.player_quality_title)
        MobilePanel.Lines -> stringResource(com.onefilm.android.tv.R.string.mobile_line_title)
        MobilePanel.Speed -> stringResource(com.onefilm.android.tv.R.string.mobile_speed_title)
        MobilePanel.Danmaku -> stringResource(ComposeR.string.player_danmaku_settings)
        MobilePanel.Subtitles -> stringResource(com.onefilm.android.tv.R.string.mobile_subtitle_title)
        MobilePanel.Tracks -> stringResource(ComposeR.string.player_track_panel)
        MobilePanel.Parse -> stringResource(ComposeR.string.player_parse)
        MobilePanel.Chapters -> stringResource(ComposeR.string.player_chapter)
        MobilePanel.Editions -> stringResource(ComposeR.string.player_edition)
        MobilePanel.More -> stringResource(ComposeR.string.common_more)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            when (panel) {
                MobilePanel.Episodes -> itemsIndexed(PlaybackSession.queue) { index, episode ->
                    MobileSheetRow(
                        text = episode.name.ifBlank { "${index + 1}" },
                        selected = index == PlaybackSession.currentIndex,
                    ) {
                        PlaybackSession.playEpisode(context, index, ComposeDependencies.playbackResolver)
                        onDismiss()
                    }
                }

                MobilePanel.Quality -> itemsIndexed(PlaybackSession.qualityOptions) { index, option ->
                    MobileSheetRow(
                        text = option.name.ifBlank { "${index + 1}" },
                        selected = index == PlaybackSession.qualityIndex,
                    ) {
                        PlaybackSession.selectQuality(index)
                        onDismiss()
                    }
                }

                MobilePanel.Lines -> itemsIndexed(PlaybackSession.lineNames) { _, name ->
                    MobileSheetRow(text = name, selected = name == PlaybackSession.flagName) {
                        PlaybackSession.switchLineTo(name)
                        onDismiss()
                    }
                }

                MobilePanel.Speed -> itemsIndexed(PlaybackSession.speedPresets) { _, value ->
                    MobileSheetRow(
                        text = PlaybackSession.speedText(value),
                        selected = kotlin.math.abs(value - PlaybackSession.speed) < 0.011f,
                    ) {
                        PlaybackSession.updateSpeed(value)
                    }
                }

                MobilePanel.Danmaku -> {
                    item {
                        MobileSheetRow(
                            text = "${stringResource(ComposeR.string.settings_danmaku_show)} " +
                                (if (DanmakuController.enabled) stringResource(ComposeR.string.common_on) else stringResource(ComposeR.string.common_off)),
                        ) {
                            val next = !DanmakuController.enabled
                            DanmakuController.updateEnabled(next)
                            PlaybackSession.legacyPlayer?.setDanmakuEnabled(next)
                        }
                    }
                    item {
                        MobileSheetRow("${stringResource(ComposeR.string.settings_danmaku_size)} ${DanmakuController.textScale}x") {
                            DanmakuController.cycleScale()
                        }
                    }
                    item {
                        MobileSheetRow("${stringResource(ComposeR.string.settings_danmaku_opacity)} ${(DanmakuController.transparency * 100).toInt()}%") {
                            DanmakuController.cycleTransparency()
                        }
                    }
                    item {
                        MobileSheetRow("${stringResource(ComposeR.string.settings_scroll_lines)} ${DanmakuController.densityLines}") {
                            DanmakuController.cycleDensity()
                        }
                    }
                    item {
                        MobileSheetRow(stringResource(ComposeR.string.settings_danmaku_style)) {
                            val modes = listOf(1, 2, 3, 0)
                            val current = com.onefilm.android.tv.setting.DanmakuSetting.getStyleMode()
                            val next = modes[(modes.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % modes.size }]
                            ComposeDependencies.settingsDataSource.setDanmakuStyle(next)
                            DanmakuController.refreshSettings()
                        }
                    }
                    item {
                        MobileSheetRow(stringResource(ComposeR.string.settings_danmaku_color)) {
                            val modes = listOf(0, 1, 2, 3, 4)
                            val current = com.onefilm.android.tv.setting.DanmakuSetting.getColorMode()
                            val next = modes[(modes.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % modes.size }]
                            ComposeDependencies.settingsDataSource.setDanmakuColorMode(next)
                            DanmakuController.refreshSettings()
                        }
                    }
                }

                MobilePanel.Subtitles -> {
                    val textTracks = PlaybackSession.tracks().filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT }
                    itemsIndexed(textTracks) { _, track ->
                        MobileSheetRow(text = track.name.ifBlank { track.format }, selected = track.selected) {
                            PlaybackSession.selectTrack(track)
                        }
                    }
                    item {
                        MobileSheetRow(stringResource(ComposeR.string.player_subtitle_import)) { onImportSubtitle() }
                    }
                    if (runCatching { ComposeDependencies.subtitleRepository.available }.getOrDefault(false)) {
                        item {
                            MobileSheetRow(stringResource(ComposeR.string.player_subtitle_search)) {
                                scope.launch {
                                    val repo = ComposeDependencies.subtitleRepository
                                    val keyword = com.onefilm.android.tv.core.danmaku.DanmakuQuery.bestName(PlaybackSession.title)
                                    val results = runCatching { repo.search(keyword) }.getOrDefault(emptyList())
                                    val first = results.firstOrNull() ?: return@launch
                                    val files = runCatching { repo.files(first.id) }.getOrDefault(emptyList())
                                    val target = files.firstOrNull { it.url.isNotBlank() } ?: return@launch
                                    val path = runCatching { repo.download(target) }.getOrNull() ?: return@launch
                                    PlaybackSession.addSubtitle(target.name, path, target.lang)
                                }
                            }
                        }
                    }
                }

                MobilePanel.Tracks -> itemsIndexed(PlaybackSession.tracks()) { _, track ->
                    MobileSheetRow(text = trackLabel(track), selected = track.selected) {
                        PlaybackSession.selectTrack(track)
                    }
                }

                MobilePanel.Parse -> {
                    item {
                        MobileSheetRow(stringResource(ComposeR.string.player_parse_default)) {
                            scope.launch { PlaybackSession.parseAndPlay(null) }
                            onDismiss()
                        }
                    }
                    itemsIndexed(engines) { _, engine ->
                        MobileSheetRow(engine.name) {
                            scope.launch { PlaybackSession.parseAndPlay(engine.name) }
                            onDismiss()
                        }
                    }
                }

                MobilePanel.Chapters -> itemsIndexed(PlaybackSession.chapters) { index, chapter ->
                    MobileSheetRow(
                        text = if (chapter.timeMs >= 0) "${chapter.label} [${formatTime(chapter.timeMs)}]" else chapter.label,
                        selected = chapter.selected,
                    ) {
                        PlaybackSession.selectChapter(index)
                    }
                }

                MobilePanel.Editions -> itemsIndexed(PlaybackSession.editions) { index, edition ->
                    MobileSheetRow(
                        text = if (edition.durationMs >= 0) "${edition.label} [${formatTime(edition.durationMs)}]" else edition.label,
                        selected = edition.selected,
                    ) {
                        PlaybackSession.selectEdition(index)
                    }
                }

                MobilePanel.More -> {
                    if (PlaybackSession.description.isNotBlank()) {
                        item { MobileSheetRow(stringResource(ComposeR.string.player_description)) { onShowDescription() } }
                    }
                    item {
                        MobileSheetRow(
                            if (PlaybackSession.favorite) stringResource(ComposeR.string.player_favorited)
                            else stringResource(ComposeR.string.player_favorite),
                        ) { PlaybackSession.toggleFavorite() }
                    }
                    if (PlaybackSession.lineNames.size > 1) {
                        item { MobileSheetRow(stringResource(com.onefilm.android.tv.R.string.mobile_line_title)) { onOpenPanel(MobilePanel.Lines) } }
                    }
                    if (PlaybackSession.qualityOptions.size > 1) {
                        item { MobileSheetRow(stringResource(ComposeR.string.player_quality_title)) { onOpenPanel(MobilePanel.Quality) } }
                    }
                    if (PlaybackSession.chapters.isNotEmpty()) {
                        item { MobileSheetRow(stringResource(ComposeR.string.player_chapter)) { onOpenPanel(MobilePanel.Chapters) } }
                    }
                    if (PlaybackSession.editions.isNotEmpty()) {
                        item { MobileSheetRow(stringResource(ComposeR.string.player_edition)) { onOpenPanel(MobilePanel.Editions) } }
                    }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_subtitle, PlaybackSession.subtitleLabel)) { onOpenPanel(MobilePanel.Subtitles) } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_track_panel)) { onOpenPanel(MobilePanel.Tracks) } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_parse)) { onOpenPanel(MobilePanel.Parse) } }
                    item { MobileSheetRow(stringResource(com.onefilm.android.tv.R.string.mobile_speed_title)) { onOpenPanel(MobilePanel.Speed) } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_resize, PlaybackSession.resizeLabel)) { PlaybackSession.cycleResizeMode() } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_engine, if (PlaybackSession.engineMpv) "MPV" else "Exo")) { PlaybackSession.setEngine(!PlaybackSession.engineMpv) } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_decode, PlaybackSession.decodeLabel)) { PlaybackSession.toggleDecode() } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_render, PlaybackSession.renderLabel)) { PlaybackSession.toggleRenderMode() } }
                    item {
                        MobileSheetRow(
                            if (PlaybackSession.repeatMode == Player.REPEAT_MODE_ONE) stringResource(ComposeR.string.player_loop_on)
                            else stringResource(ComposeR.string.player_loop_off),
                        ) { PlaybackSession.toggleRepeat() }
                    }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_refresh)) { PlaybackSession.refresh() } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_replay)) { PlaybackSession.replay() } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_cast)) { onShowCast() } }
                    item { MobileSheetRow(stringResource(ComposeR.string.player_pip)) { onPip() } }
                }
            }
        }
    }
}

@Composable
private fun MobileSheetRow(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text("✓", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlayerGlyph(glyph: String, onClick: () -> Unit) {
    Text(
        glyph,
        color = Color.White,
        fontSize = 22.sp,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

@Composable
private fun trackLabel(track: LegacyTrack): String {
    val name = track.name.ifBlank { track.format }
    return when (track.type) {
        androidx.media3.common.C.TRACK_TYPE_VIDEO -> stringResource(ComposeR.string.player_video_track, name)
        androidx.media3.common.C.TRACK_TYPE_AUDIO -> stringResource(ComposeR.string.player_audio_track, name)
        androidx.media3.common.C.TRACK_TYPE_TEXT -> stringResource(ComposeR.string.player_subtitle, name)
        else -> name
    }
}

private fun formatTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
