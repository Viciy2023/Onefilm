package com.onefilm.android.tv.mobileui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current

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
                configError = message.ifBlank { "加载失败" }
            }
        }
        if (depot) dataSource.selectDepotLine(url, name, done)
        else dataSource.loadConfig(type, url, name, done)
    }

    fun saveNew(type: Int) {
        if (configDraft.isBlank()) {
            configError = "请输入接口地址"
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
                configError = message.ifBlank { "加载失败" }
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
        item { MobileSectionTitle("接口配置") }
        item {
            MobileSettingRow("点播接口", snapshot.configUrl) { openConfig(0) }
        }
        item {
            MobileSettingRow("直播接口", liveUrl) { openConfig(1) }
        }
        item {
            MobileSettingRow("壁纸接口", snapshot.wallConfigUrl) { openConfig(2) }
        }

        item { MobileSectionTitle("外观") }
        item {
            MobileSettingRow(
                "外观主题",
                listOf("简约", "暗黑", "跟随系统").getOrElse(snapshot.themeMode) { "简约" },
            ) {
                choose("外观主题", listOf("简约", "暗黑", "跟随系统"), snapshot.themeMode) { index ->
                    dataSource.setThemeMode(index)
                    ThemeController.apply(index)
                }
            }
        }
        item {
            MobileSettingRow("壁纸切换", snapshot.wallpaperLabel) {
                dataSource.cycleWallpaper()
                refresh()
            }
        }
        item {
            MobileSettingRow("DoH", snapshot.dohLabel.ifBlank { "跟随配置" }) {
                dataSource.cycleDoh()
                refresh()
            }
        }
        item {
            MobileSwitchRow("隐身模式", "不写入历史与缓存", snapshot.incognito) {
                dataSource.setIncognito(it); refresh()
            }
        }

        item { MobileSectionTitle("字幕") }
        item {
            MobileSettingRow("字幕位置", snapshot.subtitlePosition.toInt().toString()) {
                choose(
                    "字幕位置",
                    listOf(-5f, 0f, 5f, 10f, 20f).map { it.toInt().toString() },
                    listOf(-5f, 0f, 5f, 10f, 20f).indexOf(snapshot.subtitlePosition).coerceAtLeast(0),
                ) { dataSource.setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[it]) }
            }
        }
        item { MobileSettingRow("字幕颜色", snapshot.subtitleColorLabel) { dataSource.cycleSubtitleColor(); refresh() } }
        item {
            MobileSettingRow("字幕描边", "%.0f".format(snapshot.subtitleEdgeWidth)) {
                choose(
                    "字幕描边",
                    listOf(0f, 1f, 2f, 4f).map { "%.0f".format(it) },
                    listOf(0f, 1f, 2f, 4f).indexOf(snapshot.subtitleEdgeWidth).coerceAtLeast(0),
                ) { dataSource.setSubtitleEdgeWidth(listOf(0f, 1f, 2f, 4f)[it]) }
            }
        }
        item {
            MobileSettingRow("字幕大小", "%.1fx".format(snapshot.subtitleScale)) {
                choose(
                    "字幕大小",
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).map { "%.1fx".format(it) },
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).indexOf(snapshot.subtitleScale).coerceAtLeast(0),
                ) { dataSource.setSubtitleScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[it]) }
            }
        }
        item {
            MobileSettingRow("字幕样式", listOf("原始", "系统", "自定义").getOrElse(snapshot.subtitleStyle) { "原始" }) {
                choose("字幕样式", listOf("原始", "系统", "自定义"), snapshot.subtitleStyle) {
                    dataSource.setSubtitleStyle(it)
                }
            }
        }

        item { MobileSectionTitle("弹幕") }
        item { MobileSwitchRow("显示弹幕", "加载并渲染弹幕", snapshot.danmakuShow) { dataSource.setDanmakuShow(it); refresh() } }
        item {
            MobileSettingRow("弹幕字号", "%.1fx".format(snapshot.danmakuScale)) {
                choose(
                    "弹幕字号",
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).map { "%.1fx".format(it) },
                    listOf(0.8f, 1.0f, 1.2f, 1.5f).indexOf(snapshot.danmakuScale).coerceAtLeast(0),
                ) { dataSource.setDanmakuScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[it]) }
            }
        }
        item {
            MobileSettingRow("弹幕透明度", "${(snapshot.danmakuTransparency * 100).toInt()}%") {
                choose(
                    "弹幕透明度",
                    listOf(0f, 0.25f, 0.5f, 0.7f).map { "${(it * 100).toInt()}%" },
                    listOf(0f, 0.25f, 0.5f, 0.7f).indexOf(snapshot.danmakuTransparency).coerceAtLeast(0),
                ) { dataSource.setDanmakuTransparency(listOf(0f, 0.25f, 0.5f, 0.7f)[it]) }
            }
        }
        item {
            MobileSettingRow("弹幕速度", "${snapshot.danmakuDurationMs / 1000}s") {
                choose(
                    "弹幕速度",
                    listOf(6000L, 8000L, 10000L).map { "${it / 1000}s" },
                    listOf(6000L, 8000L, 10000L).indexOf(snapshot.danmakuDurationMs).coerceAtLeast(0),
                ) { dataSource.setDanmakuDuration(listOf(6000L, 8000L, 10000L)[it]) }
            }
        }
        item {
            MobileSettingRow("滚动行数", "${snapshot.danmakuLines} 行") {
                choose(
                    "滚动行数",
                    listOf(4, 8, 12).map { "$it 行" },
                    listOf(4, 8, 12).indexOf(snapshot.danmakuLines).coerceAtLeast(0),
                ) { dataSource.setDanmakuLines(listOf(4, 8, 12)[it]) }
            }
        }
        item {
            MobileSettingRow("弹幕屏蔽词", snapshot.danmakuBlockWords.ifBlank { "未设置" }) {
                blockWordsDraft = snapshot.danmakuBlockWords
                editBlockWords = true
            }
        }

        item { MobileSectionTitle("播放") }
        item {
            MobileSettingRow("播放超时", "${snapshot.playTimeoutSeconds} 秒") {
                dataSource.cyclePlayTimeout()
                refresh()
            }
        }
        item {
            MobileSettingRow("播放引擎", if (snapshot.engineMpv) "MPV" else "Exo") {
                choose("播放引擎", listOf("Exo", "MPV"), if (snapshot.engineMpv) 1 else 0) {
                    dataSource.setEngine(it == 1)
                }
            }
        }
        item {
            MobileSettingRow("后台播放", listOf("关闭", "后台", "PiP").getOrElse(snapshot.background) { "关闭" }) {
                choose("后台播放", listOf("关闭", "后台", "PiP"), snapshot.background) {
                    dataSource.setBackground(it)
                }
            }
        }
        item {
            MobileSettingRow("缓冲区", "${snapshot.bufferSeconds} 秒") {
                choose("缓冲区", listOf("1 秒", "2 秒", "5 秒", "10 秒"), when (snapshot.bufferSeconds) {
                    1 -> 0; 2 -> 1; 5 -> 2; else -> 3
                }) { dataSource.setBufferSeconds(listOf(1, 2, 5, 10)[it]) }
            }
        }
        item {
            MobileSettingRow("渲染方式", if (snapshot.renderTexture) "Texture" else "Surface") {
                choose("渲染方式", listOf("Surface", "Texture"), if (snapshot.renderTexture) 1 else 0) {
                    dataSource.setRenderTexture(it == 1)
                }
            }
        }
        item { MobileSwitchRow("隧道解码", "需要 Surface 渲染", snapshot.tunnel) { dataSource.setTunnel(it); refresh() } }
        item { MobileSwitchRow("音频直通", "HDMI / 功放透传", snapshot.audioPassThrough) { dataSource.setAudioPassThrough(it); refresh() } }
        item { MobileSwitchRow("音频偏软解", "软解优先使用 Audio 解码器", snapshot.audioPrefer) { dataSource.setAudioPrefer(it); refresh() } }
        item { MobileSwitchRow("视频偏软解", "软解优先使用 Video 解码器", snapshot.videoPrefer) { dataSource.setVideoPrefer(it); refresh() } }
        item {
            MobileSettingRow("Dolby Vision 输出", snapshot.dvPolicyLabel) {
                dataSource.cycleDolbyVisionPolicy()
                refresh()
            }
        }

        item { MobileSectionTitle("搜索") }
        item {
            MobileSettingRow("搜索线程", "${snapshot.searchThreads}") {
                choose(
                    "搜索线程",
                    SEARCH_THREAD_OPTIONS.map { "$it" },
                    SEARCH_THREAD_OPTIONS.indexOf(snapshot.searchThreads).coerceAtLeast(0),
                ) { dataSource.setSearchThreads(SEARCH_THREAD_OPTIONS[it]) }
            }
        }
        item {
            MobileSettingRow("参与搜索的站点", "点击管理") {
                siteItems = dataSource.searchSites()
                showSites = true
            }
        }

        item { MobileSectionTitle("预加载与缓存") }
        item { MobileSwitchRow("开启预加载", "使用 PlayerManager 预加载", snapshot.preloadEnabled) { dataSource.setPreloadEnabled(it); refresh() } }
        item { MobileSwitchRow("预加载下一集", "Exo 路径生效", snapshot.preloadNext) { dataSource.setPreloadNext(it); refresh() } }
        item {
            MobileSettingRow("预加载线程", "${snapshot.preloadThreads}") {
                choose("预加载线程", listOf("1", "2", "3", "4"), (snapshot.preloadThreads - 1).coerceIn(0, 3)) {
                    dataSource.setPreloadThreads(it + 1)
                }
            }
        }
        item {
            MobileSettingRow("缓存大小", "${snapshot.preloadSizeMb} MB") {
                choose(
                    "缓存大小",
                    listOf(128, 256, 512, 1024).map { "$it MB" },
                    listOf(128, 256, 512, 1024).indexOf(snapshot.preloadSizeMb).coerceAtLeast(0),
                ) { dataSource.setPreloadSizeMb(listOf(128, 256, 512, 1024)[it]) }
            }
        }
        item {
            MobileSettingRow("缓存天数", "${snapshot.cacheDays} 天") {
                choose(
                    "缓存天数",
                    listOf(7, 30, 60, 90).map { "$it 天" },
                    listOf(7, 30, 60, 90).indexOf(snapshot.cacheDays).coerceAtLeast(0),
                ) { dataSource.setCacheDays(listOf(7, 30, 60, 90)[it]) }
            }
        }
        item { MobileSettingRow("清除缓存", snapshot.cacheSizeLabel) { showClearCacheConfirm = true } }
        item {
            MobileSettingRow("导出诊断", "写入 Download") {
                val path = dataSource.exportDiagnostics()
                Toast.makeText(
                    context,
                    if (path.isBlank()) "导出失败，请检查存储权限" else "已导出：$path",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        item { MobileSectionTitle("更新") }
        item {
            MobileSwitchRow("强制升级", "检测到新版后自动下载并强制更新", snapshot.forceUpdate) {
                dataSource.setForceUpdate(it); refresh()
            }
        }
        item { MobileSettingRow("重置 UA", "清空自定义") { dataSource.resetUa(); refresh() } }

        item { MobileSectionTitle("关于") }
        item { MobileSettingRow("版本", "${snapshot.version} · GPL-3.0") { } }
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
                            depotTitle.ifBlank { "仓库线路" },
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
                                        "线路名称（可选）",
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
                                        "请输入接口 URL",
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
                        ) { Text(if (configBusy) "加载中…" else "保存并加载", fontSize = 13.sp) }
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
                    Text("弹幕屏蔽词", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "多个关键词用逗号分隔",
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
                        TextButton(onClick = { editBlockWords = false }) { Text("取消", fontSize = 13.sp) }
                        Button(onClick = {
                            dataSource.setDanmakuBlockWords(blockWordsDraft.trim())
                            editBlockWords = false
                            refresh()
                        }) { Text("保存", fontSize = 13.sp) }
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
                        Text("参与搜索的站点", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已选 ${siteItems.count { it.enabled }} / ${siteItems.size}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            siteItems = siteItems.map { it.copy(enabled = true) }
                            dataSource.setAllSearchSites(true)
                        }) { Text("全选", fontSize = 13.sp) }
                        TextButton(onClick = {
                            siteItems = siteItems.map { it.copy(enabled = false) }
                            dataSource.setAllSearchSites(false)
                        }) { Text("全不选", fontSize = 13.sp) }
                    }
                    if (siteItems.isEmpty()) {
                        Text(
                            "当前接口没有可搜索的站点",
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
                        Button(onClick = { showSites = false }) { Text("完成", fontSize = 13.sp) }
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
                    Text("确定清除缓存？", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "将删除图片缓存和临时文件，不会删除收藏和观看记录。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(onClick = { showClearCacheConfirm = false }) { Text("取消", fontSize = 13.sp) }
                        Button(onClick = {
                            dataSource.clearCache()
                            showClearCacheConfirm = false
                            refresh()
                        }) { Text("清除", fontSize = 13.sp) }
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
                line.name.ifBlank { line.url }.ifBlank { "未命名" },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (line.current) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "当前",
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
        ) { Text("加载", fontSize = 12.sp) }
        if (showDelete) {
            TextButton(
                enabled = !busy,
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) { Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
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
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            value.ifBlank { "未设置" },
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
            Text(title, fontSize = 15.sp)
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
