@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.onefilm.android.tv.compose.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot
import com.onefilm.android.tv.compose.bridge.ServerEventBridge
import com.onefilm.android.tv.compose.bridge.SEARCH_THREAD_OPTIONS
import com.onefilm.android.tv.compose.bridge.SearchSiteItem
import com.onefilm.android.tv.compose.design.ThemeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    dataSource: SettingsDataSource,
    onConfigChanged: () -> Unit = {},
    panelMode: Boolean = false,
    onRequestClose: () -> Unit = {},
) {
    var snapshot by remember { mutableStateOf(dataSource.load()) }
    var editType by remember { mutableStateOf<Int?>(null) }
    var editUrl by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var configLoading by remember { mutableStateOf(false) }
    var reloadingType by remember { mutableStateOf<Int?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var editBlockWords by remember { mutableStateOf(false) }
    var blockWordsDraft by remember { mutableStateOf("") }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showSearchSites by remember { mutableStateOf(false) }
    var panelPage by remember { mutableStateOf(0) }
    BackHandler(enabled = panelMode) {
        if (panelPage != 0) panelPage = 0 else onRequestClose()
    }
    var configMessage by remember { mutableStateOf<String?>(null) }
    var configMessageSuccess by remember { mutableStateOf(true) }
    var messageType by remember { mutableStateOf(0) }
    val configRowFocus = remember { List(3) { FocusRequester() } }
    val firstSettingFocus = remember { FocusRequester() }
    val editFieldFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        // 只请求一次初始焦点：反复请求会在用户已经向下移动后把焦点拽回第一项。
        delay(200)
        runCatching { configRowFocus[0].requestFocus() }
    }
    LaunchedEffect(panelPage) {
        // 系统返回从二级编辑页回到列表时，把焦点还给原来的接口行。
        if (panelPage == 0) {
            val type = editType ?: return@LaunchedEffect
            delay(150)
            runCatching { configRowFocus[type].requestFocus() }
        }
    }
    LaunchedEffect(Unit) {
        val label = runCatching { dataSource.refreshCacheSize() }.getOrNull().orEmpty()
        if (label.isNotBlank()) snapshot = snapshot.copy(cacheSizeLabel = label)
    }
    LaunchedEffect(Unit) {
        ServerEventBridge.settings.collect { pushed ->
            editName = pushed.name
            editUrl = pushed.text
            configError = null
        }
    }

    fun refresh(change: SettingsDataSource.() -> Unit) {
        dataSource.change()
        snapshot = dataSource.load()
    }

    fun openConfig(type: Int, url: String) {
        editType = type
        editUrl = url
        editName = dataSource.configLines(type).firstOrNull { it.current }?.name.orEmpty()
        configError = null
        qrBitmap = dataSource.qrBitmap(dataSource.configPushUrl(), 720)
        panelPage = 1
    }

    fun reportConfig(type: Int, label: String, ok: Boolean, message: String) {
        messageType = type
        configMessageSuccess = ok
        configMessage = if (ok) {
            "${label}加载成功"
        } else {
            "${label}加载失败：${message.ifBlank { "请检查地址和网络" }}"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (panelMode) {
                    Modifier
                } else {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            ),
                        ),
                    )
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        val type = editType
        when {
            panelPage == 1 && type != null -> ConfigEditPanel(
                type = type,
                name = editName,
                onNameChange = { editName = it },
                url = editUrl,
                onUrlChange = { editUrl = it },
                loading = configLoading,
                error = configError,
                qrBitmap = qrBitmap,
                firstFieldFocus = editFieldFocus,
                onSave = {
                    configLoading = true
                    dataSource.loadConfig(type, editUrl.trim(), editName.trim()) { ok, message ->
                        configLoading = false
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                            editType = null
                            panelPage = 0
                            scope.launch {
                                delay(150)
                                runCatching { configRowFocus[type].requestFocus() }
                            }
                        } else {
                            configError = message
                        }
                    }
                },
                onBack = {
                    if (!configLoading) {
                        editType = null
                        panelPage = 0
                        scope.launch {
                            delay(150)
                            runCatching { configRowFocus[type].requestFocus() }
                        }
                    }
                },
            )
            panelPage == 2 -> BlockWordsEditPanel(
                value = blockWordsDraft,
                onValueChange = { blockWordsDraft = it },
                onSave = {
                    dataSource.setDanmakuBlockWords(blockWordsDraft.trim())
                    snapshot = dataSource.load()
                    panelPage = 0
                },
                onBack = { panelPage = 0 },
            )
            else -> {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
            item {
            SettingsGroupTitle("网络接口")
            }
            item {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                title = "点播接口",
                value = snapshot.configUrl,
                loading = reloadingType == 0,
                initialFocus = configRowFocus[0],
                onEdit = { openConfig(0, snapshot.configUrl) },
                onReload = {
                    val url = snapshot.configUrl
                    if (url.isNotBlank()) {
                        reloadingType = 0
                        dataSource.loadConfig(0, url) { ok, message ->
                            if (ok) {
                                onConfigChanged()
                                snapshot = dataSource.load()
                            }
                            reportConfig(0, "点播接口", ok, message)
                        }
                    } else {
                        reportConfig(0, "点播接口", false, "未配置接口地址")
                    }
                },
            )
            }
            item {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                title = "直播接口",
                value = snapshot.liveConfigUrl,
                loading = reloadingType == 1,
                initialFocus = configRowFocus[1],
                onEdit = { openConfig(1, snapshot.liveConfigUrl) },
                onReload = {
                    val url = snapshot.liveConfigUrl
                    if (url.isNotBlank()) {
                        reloadingType = 1
                        dataSource.loadConfig(1, url) { ok, message ->
                            if (ok) {
                                onConfigChanged()
                                snapshot = dataSource.load()
                            }
                            reportConfig(1, "直播接口", ok, message)
                        }
                    } else {
                        reportConfig(1, "直播接口", false, "未配置接口地址")
                    }
                },
            )
            }
            item {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                title = "壁纸接口",
                value = snapshot.wallConfigUrl,
                loading = reloadingType == 2,
                initialFocus = configRowFocus[2],
                onEdit = { openConfig(2, snapshot.wallConfigUrl) },
                onReload = {
                    val url = snapshot.wallConfigUrl
                    if (url.isNotBlank()) {
                        reloadingType = 2
                        dataSource.loadConfig(2, url) { ok, message ->
                            if (ok) {
                                onConfigChanged()
                                snapshot = dataSource.load()
                            }
                            reportConfig(2, "壁纸接口", ok, message)
                        }
                    } else {
                        reportConfig(2, "壁纸接口", false, "未配置接口地址")
                    }
                },
            )
            }

            item {
            SettingsGroupTitle("外观")
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "外观主题",
                    subtitle = "默认简约明亮主题",
                    options = listOf(
                        "简约" to (snapshot.themeMode == 0),
                        "暗黑" to (snapshot.themeMode == 1),
                        "跟随系统" to (snapshot.themeMode == 2),
                    ),
                ) { index ->
                    refresh { setThemeMode(index) }
                    ThemeController.apply(index)
                }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("壁纸切换", snapshot.wallpaperLabel) { refresh { cycleWallpaper() } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("DoH", snapshot.dohLabel.ifBlank { "跟随配置" }) { refresh { cycleDoh() } }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("隐身模式", "不写入历史与缓存", snapshot.incognito) {
                    refresh { setIncognito(it) }
                }
            }
            }

            item {
            SettingsGroupTitle("字幕")
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "字幕位置",
                    subtitle = snapshot.subtitlePosition.toInt().toString(),
                    options = listOf(-5f, 0f, 5f, 10f, 20f).map {
                        it.toInt().toString() to (snapshot.subtitlePosition == it)
                    },
                ) { index -> refresh { setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("字幕颜色", snapshot.subtitleColorLabel) { refresh { cycleSubtitleColor() } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "字幕描边",
                    options = listOf(0f, 1f, 2f, 4f).map {
                        "%.0f".format(it) to (snapshot.subtitleEdgeWidth == it)
                    },
                ) { index -> refresh { setSubtitleEdgeWidth(listOf(0f, 1f, 2f, 4f)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "字幕大小",
                    subtitle = "%.1fx".format(snapshot.subtitleScale),
                    options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                        "%.1fx".format(it) to (snapshot.subtitleScale == it)
                    },
                ) { index -> refresh { setSubtitleScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "字幕样式",
                    subtitle = "原始 / 系统 / 自定义",
                    options = listOf(
                        "原始" to (snapshot.subtitleStyle == 0),
                        "系统" to (snapshot.subtitleStyle == 1),
                        "自定义" to (snapshot.subtitleStyle == 2),
                    ),
                ) { index -> refresh { setSubtitleStyle(index) } }
            }
            }

            item {
            SettingsGroupTitle("弹幕")
            }
            item {
            SettingsPanelRow {
                SwitchRow("显示弹幕", "加载并渲染弹幕", snapshot.danmakuShow) {
                    refresh { setDanmakuShow(it) }
                }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "弹幕字号",
                    options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                        "%.1fx".format(it) to (snapshot.danmakuScale == it)
                    },
                ) { index -> refresh { setDanmakuScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "弹幕透明度",
                    options = listOf(0f, 0.25f, 0.5f, 0.7f).map {
                        "${(it * 100).toInt()}%" to (snapshot.danmakuTransparency == it)
                    },
                ) { index -> refresh { setDanmakuTransparency(listOf(0f, 0.25f, 0.5f, 0.7f)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "弹幕速度",
                    options = listOf(6000L, 8000L, 10000L).map {
                        "${it / 1000}s" to (snapshot.danmakuDurationMs == it)
                    },
                ) { index -> refresh { setDanmakuDuration(listOf(6000L, 8000L, 10000L)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "滚动行数",
                    options = listOf(4, 8, 12).map { "$it 行" to (snapshot.danmakuLines == it) },
                ) { index -> refresh { setDanmakuLines(listOf(4, 8, 12)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("弹幕屏蔽词", snapshot.danmakuBlockWords.ifBlank { "未设置" }) {
                    blockWordsDraft = snapshot.danmakuBlockWords
                    panelPage = 2
                }
            }
            }

            item {
            SettingsGroupTitle("播放")
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "播放引擎",
                    subtitle = "MPV 与 Exo 共用旧 PlayerManager",
                    options = listOf("Exo" to !snapshot.engineMpv, "MPV" to snapshot.engineMpv),
                ) { index -> refresh { setEngine(index == 1) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "后台播放",
                    subtitle = "按 Home 后的行为",
                    options = listOf(
                        "关闭" to (snapshot.background == 0),
                        "后台" to (snapshot.background == 1),
                        "PiP" to (snapshot.background == 2),
                    ),
                ) { index -> refresh { setBackground(index) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "缓冲区",
                    subtitle = "${snapshot.bufferSeconds} 秒",
                    options = listOf(1, 2, 5, 10).map {
                        "${it}s" to (snapshot.bufferSeconds == it)
                    },
                ) { index -> refresh { setBufferSeconds(listOf(1, 2, 5, 10)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "渲染方式",
                    subtitle = "Surface / TextureView",
                    options = listOf("Surface" to !snapshot.renderTexture, "Texture" to snapshot.renderTexture),
                ) { index -> refresh { setRenderTexture(index == 1) } }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("隧道解码", "需要 Surface 渲染", snapshot.tunnel) { refresh { setTunnel(it) } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow(
                    title = "Dolby Vision 输出",
                    value = snapshot.dvPolicyLabel,
                ) { refresh { cycleDolbyVisionPolicy() } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("播放超时", "${snapshot.playTimeoutSeconds} 秒") { refresh { cyclePlayTimeout() } }
            }
            }

            item {
            SettingsGroupTitle("音频")
            }
            item {
            SettingsPanelRow {
                SwitchRow("音频直通", "HDMI / 功放透传", snapshot.audioPassThrough) {
                    refresh { setAudioPassThrough(it) }
                }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("音频偏软解", "软解优先使用 Audio 解码器", snapshot.audioPrefer) {
                    refresh { setAudioPrefer(it) }
                }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("视频偏软解", "软解优先使用 Video 解码器", snapshot.videoPrefer) {
                    refresh { setVideoPrefer(it) }
                }
            }
            }

            item {
            SettingsGroupTitle("搜索与预加载")
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "搜索线程",
                    subtitle = "并发越高越快，越占资源",
                    options = SEARCH_THREAD_OPTIONS.map { "$it" to (snapshot.searchThreads == it) },
                ) { index ->
                    refresh { setSearchThreads(SEARCH_THREAD_OPTIONS[index]) }
                }
            }
            }
            item {
            SettingsPanelRow {
                val sites = remember(snapshot.configUrl, showSearchSites) { dataSource.searchSites() }
                val enabledCount = sites.count { it.enabled }
                ActionRow(
                    title = "参与搜索的站点",
                    value = when {
                        sites.isEmpty() -> "未加载"
                        enabledCount == sites.size -> "全部 ${sites.size} 个"
                        else -> "$enabledCount / ${sites.size}"
                    },
                ) { showSearchSites = true }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("开启预加载", "使用旧 PlayerManager 预加载", snapshot.preloadEnabled) {
                    refresh { setPreloadEnabled(it) }
                }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow("预加载下一集", "仅 Exo 路径生效", snapshot.preloadNext) {
                    refresh { setPreloadNext(it) }
                }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "预加载线程",
                    options = (1..4).map { "$it" to (snapshot.preloadThreads == it) },
                ) { index -> refresh { setPreloadThreads(index + 1) } }
            }
            }

            item {
            SettingsGroupTitle("存储与维护")
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "缓存大小",
                    options = listOf(128, 256, 512, 1024).map {
                        "$it MB" to (snapshot.preloadSizeMb == it)
                    },
                ) { index -> refresh { setPreloadSizeMb(listOf(128, 256, 512, 1024)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = "缓存天数",
                    options = listOf(7, 30, 60, 90).map {
                        "${it}天" to (snapshot.cacheDays == it)
                    },
                ) { index -> refresh { setCacheDays(listOf(7, 30, 60, 90)[index]) } }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("清除缓存", snapshot.cacheSizeLabel) {
                    showClearCacheConfirm = true
                }
            }
            }
            item {
            SettingsPanelRow {
                val context = LocalContext.current
                ActionRow("导出诊断", "写入 Download") {
                    val path = dataSource.exportDiagnostics()
                    val message = if (path.isBlank()) "导出失败，请检查存储权限" else "已导出：$path"
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            }
            item {
            SettingsPanelRow {
                ActionRow("重置 UA", "清空自定义") { refresh { resetUa() } }
            }
            }
            item {
            SettingsPanelRow {
                SwitchRow(
                    "强制升级",
                    "检测到新版本后自动下载并强制更新",
                    snapshot.forceUpdate,
                ) { refresh { setForceUpdate(it) } }
            }
            }

            item {
            SettingsGroupTitle("关于")
            }
            item {
            SettingsPanelRow {
                InfoRow("版本", "${snapshot.version} · GPL-3.0")
            }
            }
                }
            }
        }
        }


    if (showSearchSites) {
        SearchSitesDialog(
            sites = dataSource.searchSites(),
            onToggle = { key, enabled -> dataSource.setSearchSiteEnabled(key, enabled) },
            onAll = { enabled -> dataSource.setAllSearchSites(enabled) },
            onDismiss = {
                showSearchSites = false
                snapshot = dataSource.load()
            },
        )
    }

    if (showClearCacheConfirm) {
        SettingsDialog(
            onDismiss = { showClearCacheConfirm = false },
            width = 520.dp,
            padding = 16.dp,
        ) {
            Text("确定清除缓存？", fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "将删除图片缓存和临时文件，不会删除收藏和观看记录。",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
            ) {
                OutlinedButton(onClick = { showClearCacheConfirm = false }) {
                    Text("取消", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        dataSource.clearCache()
                        snapshot = dataSource.load().copy(cacheSizeLabel = "计算中…")
                        showClearCacheConfirm = false
                        scope.launch {
                            val label = runCatching { dataSource.refreshCacheSize() }.getOrNull().orEmpty()
                            if (label.isNotBlank()) snapshot = snapshot.copy(cacheSizeLabel = label)
                        }
                    },
                ) {
                    Text("清除", fontSize = 13.sp)
                }
            }
        }
    }

    configMessage?.let { message ->
        SettingsDialog(
            onDismiss = {
                configMessage = null
                scope.launch {
                    delay(150)
                    runCatching { configRowFocus[messageType].requestFocus() }
                }
            },
            width = 560.dp,
            padding = 20.dp,
        ) {
            Text(
                text = if (configMessageSuccess) "加载成功" else "加载失败",
                fontSize = 15.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = if (configMessageSuccess) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SettingsButton(
                    text = "知道了",
                    onClick = {
                        configMessage = null
                        scope.launch {
                            delay(150)
                            runCatching { configRowFocus[messageType].requestFocus() }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ConfigEditPanel(
    type: Int,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    qrBitmap: android.graphics.Bitmap?,
    firstFieldFocus: FocusRequester,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { firstFieldFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(text = "‹ 返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                when (type) {
                    0 -> "点播接口"
                    1 -> "直播接口"
                    else -> "壁纸接口"
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        CompactTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "线路名称",
            modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocus),
        )
        CompactTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = "配置 URL",
            modifier = Modifier.fillMaxWidth(),
        )
        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "配置二维码",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                "扫码打开网页配置页",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
        error?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton(
                    text = if (loading) "保存中…" else "保存",
                    onClick = onSave,
                    primary = true,
                    enabled = !loading && url.isNotBlank(),
                )
                SettingsButton(
                    text = "取消",
                    onClick = onBack,
                    enabled = !loading,
                )
            }
        }
    }
}

@Composable
private fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    primary -> MaterialTheme.colorScheme.onSurface
                    focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (primary) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BlockWordsEditPanel(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(text = "‹ 返回", onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text("弹幕屏蔽词", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = "多个关键词用逗号分隔",
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "多个关键词用逗号分隔",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(text = "保存", onClick = onSave, primary = true)
            SettingsButton(text = "取消", onClick = onBack)
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 2.dp, top = 4.dp),
    )
}

@Composable
private fun SettingsPanelRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) {
                    SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                    )
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun ConfigBlock(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    loading: Boolean,
    onEdit: () -> Unit,
    onReload: () -> Unit,
    initialFocus: FocusRequester? = null,
) {
    val editFocus = remember { FocusRequester() }
    val reloadFocus = remember { FocusRequester() }
    val rowFocus = initialFocus ?: editFocus
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingBar(Modifier.weight(1f).fillMaxHeight()) {
            ConfigRow(
                title = title,
                value = value,
                onEdit = onEdit,
                modifier = Modifier
                    .focusRequester(rowFocus)
                    .focusProperties { right = reloadFocus },
            )
        }
        ConfigLoadButton(
            loading = loading,
            enabled = value.isNotBlank(),
            onClick = onReload,
            modifier = Modifier
                .width(64.dp)
                .fillMaxHeight()
                .focusRequester(reloadFocus)
                .focusProperties { left = rowFocus },
        )
    }
}

@Composable
private fun ConfigRow(
    title: String,
    value: String,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
            )
            .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value.ifBlank { "未配置" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfigLoadButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                if (focused) 2.dp else 0.dp,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (loading) "加载中" else "加载",
            fontSize = 11.sp,
            color = if (focused) MaterialTheme.colorScheme.onPrimary
            else if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .padding(
                horizontal = 12.dp,
                vertical = if (compact) 8.dp else 12.dp,
            ),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(
                        placeholder,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun SettingsDialog(
    onDismiss: () -> Unit,
    width: Dp,
    padding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            modifier = Modifier.width(width),
        ) {
            Column(
                Modifier.padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}

@Composable
private fun SettingBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    ),
                ),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            ),
    ) {
        content()
    }
}

@Composable
private fun ExpandableChoiceRow(
    title: String,
    subtitle: String? = null,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val firstChipFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var firstComposition by remember { mutableStateOf(true) }
    val selectedLabel = options.firstOrNull { it.second }?.first ?: "未选择"
    LaunchedEffect(expanded) {
        if (firstComposition) {
            firstComposition = false
            return@LaunchedEffect
        }
        delay(150)
        runCatching {
            if (expanded) firstChipFocus.requestFocus() else rowFocus.requestFocus()
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .focusRequester(rowFocus)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                selectedLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (expanded) "︿" else "›",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(expanded) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEachIndexed { index, (label, selected) ->
                    SettingChip(
                        label = label,
                        selected = selected,
                        focusRequester = if (index == 0) firstChipFocus else null,
                    ) {
                        onSelect(index)
                        expanded = false
                        scope.launch {
                            delay(120)
                            runCatching { rowFocus.requestFocus() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    // The switch is edited in the same option dialog as ChoiceRow.
    ExpandableChoiceRow(
        title = title,
        subtitle = subtitle,
        options = listOf(
            "关闭" to !checked,
            "开启" to checked,
        ),
    ) { index -> onChange(index == 1) }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var dialogVisible by remember { mutableStateOf(false) }
    var draftIndex by remember { mutableIntStateOf(options.indexOfFirst { it.second }) }
    val firstOptionFocus = remember { FocusRequester() }
    val selectedLabel = options.firstOrNull { it.second }?.first ?: "未选择"
    LaunchedEffect(dialogVisible) {
        if (dialogVisible) {
            delay(80)
            runCatching { firstOptionFocus.requestFocus() }
        }
    }
    Row(
        Modifier
            .fillMaxSize()
            .onFocusChanged { focused = it.isFocused }
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .clickable {
                draftIndex = options.indexOfFirst { it.second }
                dialogVisible = true
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            selectedLabel,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 96.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (dialogVisible) {
        SettingsDialog(
            onDismiss = { dialogVisible = false },
            width = when {
                options.size <= 2 -> 260.dp
                options.size == 3 -> 300.dp
                options.size == 4 -> 340.dp
                else -> 380.dp
            },
            padding = 10.dp,
        ) {
            Text(
                title,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 5.dp, bottom = 6.dp),
                )
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEachIndexed { index, (label, _) ->
                    val selected = draftIndex == index
                    var optionFocused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .onFocusChanged { optionFocused = it.isFocused }
                            .border(
                                2.dp,
                                if (optionFocused) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
                                RoundedCornerShape(10.dp),
                            )
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .then(
                                if (index == 0) {
                                    Modifier.focusRequester(firstOptionFocus)
                                } else Modifier,
                            )
                            .clickable {
                                onSelect(index)
                                dialogVisible = false
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = if (selected) MaterialTheme.colorScheme.background
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier,
            )
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.onSurface
                    focused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                when {
                    focused && selected -> MaterialTheme.colorScheme.background
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                },
                RoundedCornerShape(8.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = if (selected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ActionRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 120.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            value,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 110.dp),
        )
    }
}

@Composable
private fun SearchSitesDialog(
    sites: List<SearchSiteItem>,
    onToggle: (String, Boolean) -> Unit,
    onAll: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val states = remember(sites) {
        mutableStateMapOf<String, Boolean>().apply {
            sites.forEach { put(it.key, it.enabled) }
        }
    }
    val firstRowFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { firstRowFocus.requestFocus() }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
            modifier = Modifier.width(760.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "参与搜索的站点",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "已选 ${states.count { it.value }} / ${sites.size}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    DialogAction("全选") {
                        sites.forEach { states[it.key] = true }
                        onAll(true)
                    }
                    Spacer(Modifier.width(8.dp))
                    DialogAction("全不选") {
                        sites.forEach { states[it.key] = false }
                        onAll(false)
                    }
                    Spacer(Modifier.width(8.dp))
                    DialogAction("完成", onDismiss)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "取消勾选的站点不会参与搜索，可随时恢复。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (sites.isEmpty()) {
                    Text(
                        "当前接口没有可搜索的站点",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                } else {
                    // 两列铺满卡片，避免「站点名」和「站点标识」之间出现大片空白
                    val rows = (sites.size + 1) / 2
                    val listHeight = (rows * 46 - 6).coerceIn(86, 316).dp
                    LazyColumn(
                        Modifier.fillMaxWidth().height(listHeight),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(sites.chunked(2)) { rowIndex, pair ->
                            Row(
                                Modifier.fillMaxWidth().height(40.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                pair.forEachIndexed { columnIndex, site ->
                                    SiteToggleRow(
                                        site = site,
                                        checked = states[site.key] == true,
                                        modifier = Modifier.weight(1f).then(
                                            if (rowIndex == 0 && columnIndex == 0) {
                                                Modifier.focusRequester(firstRowFocus)
                                            } else {
                                                Modifier
                                            },
                                        ),
                                        onToggle = {
                                            val next = !(states[site.key] == true)
                                            states[site.key] = next
                                            onToggle(site.key, next)
                                        },
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteToggleRow(
    site: SearchSiteItem,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            )
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 不用 ☑/☐ 字符：电视字体常常缺这些字形，会显示成方块
        Box(
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                )
                .border(
                    1.5.dp,
                    if (checked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    RoundedCornerShape(4.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onPrimary),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            site.name.ifBlank { site.key },
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            site.key,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 110.dp),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DialogAction(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .border(
                2.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(10.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            color = if (focused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}
