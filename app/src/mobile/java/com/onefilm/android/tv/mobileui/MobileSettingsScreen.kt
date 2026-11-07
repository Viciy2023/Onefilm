package com.onefilm.android.tv.mobileui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SEARCH_THREAD_OPTIONS
import com.onefilm.android.tv.compose.bridge.SearchSiteItem
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.design.ThemeController
import kotlinx.coroutines.launch
import com.onefilm.android.tv.R
import com.github.catvod.utils.Prefers

private data class MobileChoice(
    val title: String,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

@Composable
fun MobileSettingsScreen(
    dataSource: SettingsDataSource,
    onConfigChanged: () -> Unit = {},
) {
    var snapshot by remember { mutableStateOf(dataSource.load()) }
    var choice by remember { mutableStateOf<MobileChoice?>(null) }
    var configType by remember { mutableStateOf<Int?>(null) }
    var configName by remember { mutableStateOf("") }
    var configDraft by remember { mutableStateOf("") }
    var configBusy by remember { mutableStateOf(false) }
    var configError by remember { mutableStateOf<String?>(null) }
    var configLines by remember { mutableStateOf<List<ConfigLine>>(emptyList()) }
    var depotTitle by remember { mutableStateOf("") }
    var depotLines by remember { mutableStateOf<List<ConfigLine>>(emptyList()) }
    var blockWordsDraft by remember { mutableStateOf("") }
    var editBlockWords by remember { mutableStateOf(false) }
    var showSites by remember { mutableStateOf(false) }
    var siteItems by remember { mutableStateOf<List<SearchSiteItem>>(emptyList()) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        latestVersion = runCatching { dataSource.latestVersion() }.getOrNull().orEmpty()
    }

    fun refresh() {
        snapshot = dataSource.load()
    }

    fun choose(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        choice = MobileChoice(title, options, selected) { index ->
            onSelect(index)
            choice = null
            refresh()
        }
    }

    /** 只负责打开弹窗；新增/修改的输入框始终是空的，不回填当前接口。 */
    fun openConfig(type: Int) {
        configType = type
        configDraft = ""
        configName = ""
        configError = null
        configLines = runCatching { dataSource.configLines(type) }.getOrDefault(emptyList())
        if (type == 0) {
            depotTitle = runCatching { dataSource.depotName() }.getOrDefault("")
            depotLines = runCatching { dataSource.depotLines() }.getOrDefault(emptyList())
        } else {
            depotTitle = ""
            depotLines = emptyList()
        }
    }

    fun loadSaved(type: Int, url: String, name: String, depot: Boolean) {
        if (url.isBlank()) return
        configBusy = true
        configError = null
        val done: (Boolean, String) -> Unit = { ok, message ->
            configBusy = false
            if (ok) {
                configType = null
                onConfigChanged()
                refresh()
            } else {
                configError = message.ifBlank { context.getString(R.string.mobile_settings_load_failed) }
            }
        }
        if (depot) dataSource.selectDepotLine(url, name, done)
        else dataSource.loadConfig(type, url, name, done)
    }

    fun saveNew(type: Int) {
        if (configDraft.isBlank()) {
            configError = context.getString(R.string.mobile_settings_enter_url)
            return
        }
        configBusy = true
        configError = null
        if (type == 1) Prefers.put("compose_live_url", configDraft.trim())
        dataSource.loadConfig(type, configDraft.trim(), configName.trim()) { ok, message ->
            configBusy = false
            if (ok) {
                configType = null
                onConfigChanged()
                refresh()
            } else {
                configError = message.ifBlank { context.getString(R.string.mobile_settings_load_failed) }
            }
        }
    }

    val liveUrl = Prefers.getString("compose_live_url").orEmpty()
        .ifBlank { snapshot.liveConfigUrl }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_api)) }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_vod_api), snapshot.configUrl) { openConfig(0) }
        }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_live_api), liveUrl) { openConfig(1) }
        }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_wall_api), snapshot.wallConfigUrl) { openConfig(2) }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_appearance)) }
        item {
            val themeTitle = stringResource(R.string.mobile_settings_theme)
            val themeLabels = listOf(
                stringResource(R.string.mobile_settings_theme_minimal),
                stringResource(R.string.mobile_settings_theme_dark),
                stringResource(R.string.mobile_settings_theme_system),
            )
            MobileSettingRow(
                themeTitle,
                themeLabels.getOrElse(snapshot.themeMode) { themeLabels.first() },
            ) {
                choose(themeTitle, themeLabels, snapshot.themeMode) { index ->
                    dataSource.setThemeMode(index)
                    ThemeController.apply(index)
                }
            }
        }
        item {
            val languageTitle = stringResource(R.string.mobile_settings_language)
            val languageTags = com.onefilm.android.tv.compose.bridge.LanguageBridge.tags()
            val languageCurrent = com.onefilm.android.tv.compose.bridge.LanguageBridge.current()
            MobileSettingRow(
                languageTitle,
                com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf(languageCurrent),
            ) {
                choose(
                    languageTitle,
                    languageTags.map { com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf(it) },
                    languageTags.indexOf(languageCurrent).coerceAtLeast(0),
                ) { index ->
                    languageTags.getOrNull(index)?.let {
                        com.onefilm.android.tv.compose.bridge.LanguageBridge.apply(it)
                    }
                }
            }
        }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_wallpaper), snapshot.wallpaperLabel) {
                dataSource.cycleWallpaper()
                refresh()
            }
        }
        item {
            MobileSettingRow("DoH", snapshot.dohLabel.ifBlank { stringResource(R.string.mobile_settings_follow_config) }) {
                dataSource.cycleDoh()
                refresh()
            }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_incognito),
                stringResource(R.string.mobile_settings_incognito_desc),
                snapshot.incognito,
            ) {
                dataSource.setIncognito(it); refresh()
            }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_subtitle)) }
        item {
            val positionTitle = stringResource(R.string.mobile_settings_subtitle_position)
            MobileSettingRow(positionTitle, snapshot.subtitlePosition.toInt().toString()) {
                choose(
                    positionTitle,
                    listOf(-5f, 0f, 5f, 10f, 20f).map { it.toInt().toString() },
                    listOf(-5f, 0f, 5f, 10f, 20f).indexOf(snapshot.subtitlePosition).coerceAtLeast(0),
                ) { dataSource.setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[it]) }
            }
        }
        item { MobileSettingRow(stringResource(R.string.mobile_settings_subtitle_color), snapshot.subtitleColorLabel) { dataSource.cycleSubtitleColor(); refresh() } }
        item {
            val edgeTitle = stringResource(R.string.mobile_settings_subtitle_edge)
            MobileSettingRow(edgeTitle, "%.0f".format(snapshot.subtitleEdgeWidth)) {
                choose(
                    edgeTitle,
                    listOf(0f, 1f, 2f, 4f).map { "%.0f".format(it) },
                    listOf(0f, 1f, 2f, 4f).indexOf(snapshot.subtitleEdgeWidth).coerceAtLeast(0),
                ) { dataSource.setSubtitleEdgeWidth(listOf(0f, 1f, 2f, 4f)[it]) }
            }
        }
        item {
            val sizeTitle = stringResource(R.string.mobile_settings_subtitle_size)
            MobileSettingRow(sizeTitle, "%.1fx".format(snapshot.subtitleScale)) {
                choose(
                    sizeTitle,
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).map { "%.1fx".format(it) },
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).indexOf(snapshot.subtitleScale).coerceAtLeast(0),
                ) { dataSource.setSubtitleScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[it]) }
            }
        }
        item {
            val styleTitle = stringResource(R.string.mobile_settings_subtitle_style)
            val styleLabels = listOf(
                stringResource(R.string.mobile_settings_style_original),
                stringResource(R.string.mobile_settings_style_system),
                stringResource(R.string.mobile_settings_style_custom),
            )
            MobileSettingRow(
                styleTitle,
                styleLabels.getOrElse(snapshot.subtitleStyle) { styleLabels.first() },
            ) {
                choose(styleTitle, styleLabels, snapshot.subtitleStyle) {
                    dataSource.setSubtitleStyle(it)
                }
            }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_danmaku)) }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_danmaku_show),
                stringResource(R.string.mobile_settings_danmaku_show_desc),
                snapshot.danmakuShow,
            ) { dataSource.setDanmakuShow(it); refresh() }
        }
        item {
            val danmakuSizeTitle = stringResource(R.string.mobile_settings_danmaku_size)
            MobileSettingRow(danmakuSizeTitle, "%.1fx".format(snapshot.danmakuScale)) {
                choose(
                    danmakuSizeTitle,
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).map { "%.1fx".format(it) },
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).indexOf(snapshot.danmakuScale).coerceAtLeast(0),
                ) { dataSource.setDanmakuScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[it]) }
            }
        }
        item {
            val alphaTitle = stringResource(R.string.mobile_settings_danmaku_alpha)
            MobileSettingRow(alphaTitle, "${(snapshot.danmakuTransparency * 100).toInt()}%") {
                choose(
                    alphaTitle,
                    listOf(0f, 0.25f, 0.5f, 0.7f).map { "${(it * 100).toInt()}%" },
                    listOf(0f, 0.25f, 0.5f, 0.7f).indexOf(snapshot.danmakuTransparency).coerceAtLeast(0),
                ) { dataSource.setDanmakuTransparency(listOf(0f, 0.25f, 0.5f, 0.7f)[it]) }
            }
        }
        item {
            val speedTitle = stringResource(R.string.mobile_settings_danmaku_speed)
            MobileSettingRow(speedTitle, "${snapshot.danmakuDurationMs / 1000}s") {
                choose(
                    speedTitle,
                    listOf(6000L, 8000L, 10000L).map { "${it / 1000}s" },
                    listOf(6000L, 8000L, 10000L).indexOf(snapshot.danmakuDurationMs).coerceAtLeast(0),
                ) { dataSource.setDanmakuDuration(listOf(6000L, 8000L, 10000L)[it]) }
            }
        }
        item {
            val linesTitle = stringResource(R.string.mobile_settings_danmaku_lines)
            val linesOptions = listOf(
                stringResource(R.string.mobile_settings_danmaku_lines_value, 4),
                stringResource(R.string.mobile_settings_danmaku_lines_value, 8),
                stringResource(R.string.mobile_settings_danmaku_lines_value, 12),
            )
            MobileSettingRow(
                linesTitle,
                stringResource(R.string.mobile_settings_danmaku_lines_value, snapshot.danmakuLines),
            ) {
                choose(
                    linesTitle,
                    linesOptions,
                    listOf(4, 8, 12).indexOf(snapshot.danmakuLines).coerceAtLeast(0),
                ) { dataSource.setDanmakuLines(listOf(4, 8, 12)[it]) }
            }
        }
        item {
            val blockTitle = stringResource(R.string.mobile_settings_danmaku_block)
            val notSet = stringResource(R.string.mobile_settings_not_set)
            MobileSettingRow(blockTitle, snapshot.danmakuBlockWords.ifBlank { notSet }) {
                blockWordsDraft = snapshot.danmakuBlockWords
                editBlockWords = true
            }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_playback)) }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_adblock),
                stringResource(R.string.mobile_settings_adblock_desc),
                snapshot.adblock,
            ) {
                dataSource.setAdblock(it); refresh()
            }
        }
        item {
            MobileSettingRow(
                stringResource(R.string.mobile_settings_play_timeout),
                stringResource(R.string.mobile_settings_seconds_value, snapshot.playTimeoutSeconds),
            ) {
                dataSource.cyclePlayTimeout()
                refresh()
            }
        }
        item {
            val engineTitle = stringResource(R.string.mobile_settings_play_engine)
            MobileSettingRow(engineTitle, if (snapshot.engineMpv) "MPV" else "Exo") {
                choose(engineTitle, listOf("Exo", "MPV"), if (snapshot.engineMpv) 1 else 0) {
                    dataSource.setEngine(it == 1)
                }
            }
        }
        item {
            val backgroundTitle = stringResource(R.string.mobile_settings_background)
            val backgroundLabels = listOf(
                stringResource(R.string.mobile_settings_background_off),
                stringResource(R.string.mobile_settings_background_bg),
                "PiP",
            )
            MobileSettingRow(
                backgroundTitle,
                backgroundLabels.getOrElse(snapshot.background) { backgroundLabels.first() },
            ) {
                choose(backgroundTitle, backgroundLabels, snapshot.background) {
                    dataSource.setBackground(it)
                }
            }
        }
        item {
            val bufferTitle = stringResource(R.string.mobile_settings_buffer)
            val bufferOptions = listOf(
                stringResource(R.string.mobile_settings_seconds_value, 1),
                stringResource(R.string.mobile_settings_seconds_value, 2),
                stringResource(R.string.mobile_settings_seconds_value, 5),
                stringResource(R.string.mobile_settings_seconds_value, 10),
            )
            MobileSettingRow(
                bufferTitle,
                stringResource(R.string.mobile_settings_seconds_value, snapshot.bufferSeconds),
            ) {
                choose(
                    bufferTitle,
                    bufferOptions,
                    when (snapshot.bufferSeconds) {
                        1 -> 0; 2 -> 1; 5 -> 2; else -> 3
                    },
                ) { dataSource.setBufferSeconds(listOf(1, 2, 5, 10)[it]) }
            }
        }
        item {
            val renderTitle = stringResource(R.string.mobile_settings_render_mode)
            MobileSettingRow(renderTitle, if (snapshot.renderTexture) "Texture" else "Surface") {
                choose(renderTitle, listOf("Surface", "Texture"), if (snapshot.renderTexture) 1 else 0) {
                    dataSource.setRenderTexture(it == 1)
                }
            }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_tunnel),
                stringResource(R.string.mobile_settings_tunnel_desc),
                snapshot.tunnel,
            ) { dataSource.setTunnel(it); refresh() }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_audio_passthrough),
                stringResource(R.string.mobile_settings_audio_passthrough_desc),
                snapshot.audioPassThrough,
            ) { dataSource.setAudioPassThrough(it); refresh() }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_audio_soft),
                stringResource(R.string.mobile_settings_audio_soft_desc),
                snapshot.audioPrefer,
            ) { dataSource.setAudioPrefer(it); refresh() }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_video_soft),
                stringResource(R.string.mobile_settings_video_soft_desc),
                snapshot.videoPrefer,
            ) { dataSource.setVideoPrefer(it); refresh() }
        }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_dv_output), snapshot.dvPolicyLabel) {
                dataSource.cycleDolbyVisionPolicy()
                refresh()
            }
        }

        item { MobileSectionTitle(stringResource(R.string.menu_search)) }
        item {
            val threadsTitle = stringResource(R.string.mobile_settings_search_threads)
            MobileSettingRow(threadsTitle, "${snapshot.searchThreads}") {
                choose(
                    threadsTitle,
                    SEARCH_THREAD_OPTIONS.map { "$it" },
                    SEARCH_THREAD_OPTIONS.indexOf(snapshot.searchThreads).coerceAtLeast(0),
                ) { dataSource.setSearchThreads(SEARCH_THREAD_OPTIONS[it]) }
            }
        }
        item {
            MobileSettingRow(stringResource(R.string.mobile_settings_search_sites), stringResource(R.string.mobile_settings_manage)) {
                siteItems = dataSource.searchSites()
                showSites = true
            }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_cache)) }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_preload),
                stringResource(R.string.mobile_settings_preload_desc),
                snapshot.preloadEnabled,
            ) { dataSource.setPreloadEnabled(it); refresh() }
        }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_preload_next),
                stringResource(R.string.mobile_settings_preload_next_desc),
                snapshot.preloadNext,
            ) { dataSource.setPreloadNext(it); refresh() }
        }
        item {
            val preloadThreadsTitle = stringResource(R.string.mobile_settings_preload_threads)
            MobileSettingRow(preloadThreadsTitle, "${snapshot.preloadThreads}") {
                choose(preloadThreadsTitle, listOf("1", "2", "3", "4"), (snapshot.preloadThreads - 1).coerceIn(0, 3)) {
                    dataSource.setPreloadThreads(it + 1)
                }
            }
        }
        item {
            val cacheSizeTitle = stringResource(R.string.mobile_settings_cache_size)
            MobileSettingRow(cacheSizeTitle, "${snapshot.preloadSizeMb} MB") {
                choose(
                    cacheSizeTitle,
                    listOf(128, 256, 512, 1024).map { "$it MB" },
                    listOf(128, 256, 512, 1024).indexOf(snapshot.preloadSizeMb).coerceAtLeast(0),
                ) { dataSource.setPreloadSizeMb(listOf(128, 256, 512, 1024)[it]) }
            }
        }
        item {
            val cacheDaysTitle = stringResource(R.string.mobile_settings_cache_days)
            val cacheDaysOptions = listOf(
                stringResource(R.string.mobile_settings_days_value, 7),
                stringResource(R.string.mobile_settings_days_value, 30),
                stringResource(R.string.mobile_settings_days_value, 60),
                stringResource(R.string.mobile_settings_days_value, 90),
            )
            MobileSettingRow(
                cacheDaysTitle,
                stringResource(R.string.mobile_settings_days_value, snapshot.cacheDays),
            ) {
                choose(
                    cacheDaysTitle,
                    cacheDaysOptions,
                    listOf(7, 30, 60, 90).indexOf(snapshot.cacheDays).coerceAtLeast(0),
                ) { dataSource.setCacheDays(listOf(7, 30, 60, 90)[it]) }
            }
        }
        item { MobileSettingRow(stringResource(R.string.mobile_settings_clear_cache), snapshot.cacheSizeLabel) { showClearCacheConfirm = true } }
        item {
            MobileSettingRow(
                stringResource(R.string.mobile_settings_export),
                stringResource(R.string.mobile_settings_export_desc),
            ) {
                val path = dataSource.exportDiagnostics()
                val failedText = context.getString(R.string.mobile_settings_export_failed)
                val doneText = context.getString(R.string.mobile_settings_export_ok, path)
                val message = if (path.isBlank()) failedText else doneText
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_update)) }
        item {
            MobileSwitchRow(
                stringResource(R.string.mobile_settings_force_update),
                stringResource(R.string.mobile_settings_force_update_desc),
                snapshot.forceUpdate,
            ) {
                dataSource.setForceUpdate(it); refresh()
            }
        }
        item {
            MobileSettingRow(
                stringResource(R.string.mobile_settings_reset_ua),
                stringResource(R.string.mobile_settings_reset_ua_desc),
            ) { dataSource.resetUa(); refresh() }
        }

        item { MobileSectionTitle(stringResource(R.string.mobile_settings_section_about)) }
        item { MobileSettingRow(stringResource(R.string.mobile_settings_version), "${snapshot.version} · GPL-3.0") { } }
        item {
            MobileSettingRow(
                stringResource(R.string.mobile_settings_latest_version),
                latestVersion?.takeIf { it.isNotBlank() } ?: "—",
            ) { }
        }
        item {
            MobileSettingRow(
                stringResource(R.string.mobile_settings_contact_us),
                stringResource(R.string.mobile_settings_contact_us_desc),
            ) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+I6pu2Yd2SAJjZTJl")),
                    )
                }
            }
        }
    }

    choice?.let { current ->
        Dialog(
            onDismissRequest = { choice = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    current.options.forEachIndexed { index, option ->
                        TextButton(
                            onClick = { current.onSelect(index) },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp),
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                option,
                                maxLines = 1,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (index == current.selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }

    configType?.let { type ->
        Dialog(
            onDismissRequest = { if (!configBusy) configType = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    if (configLines.isNotEmpty()) {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(configLines, key = { "${it.type}:${it.url}" }) { line ->
                                ConfigLineRow(
                                    line = line,
                                    busy = configBusy,
                                    onLoad = { loadSaved(type, line.url, line.name, depot = false) },
                                    onDelete = {
                                        runCatching { dataSource.deleteConfigLine(type, line.url) }
                                        configLines = runCatching { dataSource.configLines(type) }
                                            .getOrDefault(emptyList())
                                        refresh()
                                    },
                                )
                            }
                        }
                    }

                    if (depotLines.isNotEmpty()) {
                        Text(
                            depotTitle.ifBlank { stringResource(R.string.mobile_settings_repo_lines) },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(depotLines, key = { "depot:${it.url}" }) { line ->
                                ConfigLineRow(
                                    line = line,
                                    busy = configBusy,
                                    showDelete = false,
                                    onLoad = { loadSaved(0, line.url, line.name, depot = true) },
                                    onDelete = {},
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = configName,
                        onValueChange = { configName = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (configName.isEmpty()) {
                                    Text(
                                        stringResource(R.string.mobile_settings_line_name_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    BasicTextField(
                        value = configDraft,
                        onValueChange = { configDraft = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp),
                        decorationBox = { innerTextField ->
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                                if (configDraft.isEmpty()) {
                                    Text(
                                        stringResource(R.string.mobile_settings_url_hint),
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                    if (!configError.isNullOrBlank()) {
                        Text(
                            configError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        Button(
                            enabled = !configBusy,
                            onClick = { saveNew(type) },
                        ) { Text(if (configBusy) stringResource(R.string.mobile_settings_loading) else stringResource(R.string.mobile_settings_save_load), fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (editBlockWords) {
        Dialog(
            onDismissRequest = { editBlockWords = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mobile_settings_danmaku_block), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.mobile_settings_danmaku_block_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BasicTextField(
                        value = blockWordsDraft,
                        onValueChange = { blockWordsDraft = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { editBlockWords = false }) { Text(stringResource(R.string.mobile_settings_cancel), fontSize = 13.sp) }
                        Button(onClick = {
                            dataSource.setDanmakuBlockWords(blockWordsDraft.trim())
                            editBlockWords = false
                            refresh()
                        }) { Text(stringResource(R.string.mobile_settings_save), fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (showSites) {
        Dialog(
            onDismissRequest = { showSites = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.mobile_settings_search_sites), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.mobile_settings_sites_selected, siteItems.count { it.enabled }, siteItems.size),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            siteItems = siteItems.map { it.copy(enabled = true) }
                            dataSource.setAllSearchSites(true)
                        }) { Text(stringResource(R.string.mobile_settings_select_all), fontSize = 13.sp) }
                        TextButton(onClick = {
                            siteItems = siteItems.map { it.copy(enabled = false) }
                            dataSource.setAllSearchSites(false)
                        }) { Text(stringResource(R.string.mobile_settings_select_none), fontSize = 13.sp) }
                    }
                    if (siteItems.isEmpty()) {
                        Text(
                            stringResource(R.string.mobile_settings_sites_empty),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().heightIn(max = 420.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(siteItems, key = { it.key }) { site ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                        .clickable {
                                            val next = !site.enabled
                                            siteItems = siteItems.map {
                                                if (it.key == site.key) it.copy(enabled = next) else it
                                            }
                                            dataSource.setSearchSiteEnabled(site.key, next)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        site.name.ifBlank { site.key },
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Switch(checked = site.enabled, onCheckedChange = { next ->
                                        siteItems = siteItems.map {
                                            if (it.key == site.key) it.copy(enabled = next) else it
                                        }
                                        dataSource.setSearchSiteEnabled(site.key, next)
                                    })
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(onClick = { showSites = false }) { Text(stringResource(R.string.mobile_settings_done), fontSize = 13.sp) }
                    }
                }
            }
        }
    }

    if (showClearCacheConfirm) {
        Dialog(
            onDismissRequest = { showClearCacheConfirm = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.mobile_settings_clear_cache_confirm), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        stringResource(R.string.mobile_settings_clear_cache_desc),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { showClearCacheConfirm = false }) { Text(stringResource(R.string.mobile_settings_cancel), fontSize = 13.sp) }
                        Button(onClick = {
                            dataSource.clearCache()
                            showClearCacheConfirm = false
                            snapshot = snapshot.copy(cacheSizeLabel = context.getString(R.string.mobile_settings_loading))
                            scope.launch {
                                val label = runCatching { dataSource.refreshCacheSize() }.getOrNull().orEmpty()
                                if (label.isNotBlank()) snapshot = snapshot.copy(cacheSizeLabel = label)
                            }
                        }) { Text(stringResource(R.string.mobile_settings_clear), fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

/**
 * 已保存接口 / 仓库线路的一行，扁平样式：
 * 左侧只显示名称（没有名称时用 URL 省略显示），右侧「加载」+「删除」。
 */
@Composable
private fun ConfigLineRow(
    line: ConfigLine,
    busy: Boolean,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    showDelete: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                line.name.ifBlank { line.url }.ifBlank { stringResource(R.string.mobile_unnamed) },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (line.current) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.mobile_settings_current),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
        TextButton(
            enabled = !busy,
            onClick = onLoad,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) { Text(stringResource(R.string.mobile_settings_load), fontSize = 12.sp) }
        if (showDelete) {
            TextButton(
                enabled = !busy,
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text(stringResource(R.string.menu_delete), fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MobileSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun MobileSettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            value.ifBlank { stringResource(R.string.mobile_settings_not_set) },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MobileSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
