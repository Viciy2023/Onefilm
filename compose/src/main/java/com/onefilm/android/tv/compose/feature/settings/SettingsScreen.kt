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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.onefilm.android.tv.compose.design.component.TvTagButton
import com.onefilm.android.tv.compose.bridge.ServerEventBridge
import com.onefilm.android.tv.compose.bridge.SEARCH_THREAD_OPTIONS
import com.onefilm.android.tv.compose.bridge.SearchSiteItem
import com.onefilm.android.tv.compose.design.ThemeController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource

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
    var showContact by remember { mutableStateOf(false) }
    var reloadingDanmaku by remember { mutableStateOf(false) }
    var panelPage by remember { mutableStateOf(0) }
    var latestVersion by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = panelMode) {
        if (panelPage != 0) panelPage = 0 else onRequestClose()
    }
    var configMessage by remember { mutableStateOf<String?>(null) }
    var configMessageSuccess by remember { mutableStateOf(true) }
    var messageType by remember { mutableStateOf(0) }
    val configRowFocus = remember { List(3) { FocusRequester() } }
    val danmakuApiFocus = remember { FocusRequester() }
    val blockWordsFocus = remember { FocusRequester() }
    val contactQrBringIntoView = remember { BringIntoViewRequester() }
    val listState = rememberLazyGridState()
    val firstSettingFocus = remember { FocusRequester() }
    val editFieldFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val resContext = LocalContext.current
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
            if (type in configRowFocus.indices) runCatching { configRowFocus[type].requestFocus() }
        }
    }
    LaunchedEffect(showContact) {
        // 二维码在“联系我们”上方展开后，把列表上滑，保证整张二维码完整可见。
        if (showContact) {
            delay(180)
            runCatching { contactQrBringIntoView.bringIntoView() }
        }
    }
    LaunchedEffect(Unit) {
        val label = runCatching { dataSource.refreshCacheSize() }.getOrNull().orEmpty()
        if (label.isNotBlank()) snapshot = snapshot.copy(cacheSizeLabel = label)
    }
    LaunchedEffect(Unit) {
        latestVersion = runCatching { dataSource.latestVersion() }.getOrNull().orEmpty()
    }
    LaunchedEffect(Unit) {
        ServerEventBridge.settings.collect { pushed ->
            if (pushed.name.equals("danmaku", true) || pushed.name == "弹幕") {
                dataSource.setDanmakuApi(pushed.text.trim())
                snapshot = dataSource.load()
            } else {
                editName = pushed.name
                editUrl = pushed.text
                configError = null
            }
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
            resContext.getString(R.string.settings_load_ok, label)
        } else {
            resContext.getString(R.string.settings_load_fail, label, message.ifBlank { resContext.getString(R.string.settings_check_url) })
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
                    if (type == 3) {
                        dataSource.setDanmakuApi(editUrl.trim())
                        snapshot = dataSource.load()
                        editType = null
                        panelPage = 0
                        scope.launch {
                            delay(200)
                            runCatching { danmakuApiFocus.requestFocus() }
                        }
                    } else {
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
                    }
                },
                onBack = {
                    if (!configLoading) {
                        editType = null
                        panelPage = 0
                        scope.launch {
                            delay(200)
                            if (type == 3) {
                                runCatching { danmakuApiFocus.requestFocus() }
                            } else if (type in configRowFocus.indices) {
                                runCatching { configRowFocus[type].requestFocus() }
                            }
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
                    scope.launch {
                        delay(200)
                        runCatching { blockWordsFocus.requestFocus() }
                    }
                },
                onBack = {
                    panelPage = 0
                    scope.launch {
                        delay(200)
                        runCatching { blockWordsFocus.requestFocus() }
                    }
                },
            )
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_network))
            }
            item(span = { GridItemSpan(2) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                title = stringResource(R.string.settings_vod_url),
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
                            reportConfig(0, resContext.getString(R.string.settings_vod_url), ok, message)
                        }
                    } else {
                        reportConfig(0, resContext.getString(R.string.settings_vod_url), false, resContext.getString(R.string.settings_no_config_url))
                    }
                },
            )
            }
            item(span = { GridItemSpan(2) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                title = stringResource(R.string.settings_live_url),
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
                            reportConfig(1, resContext.getString(R.string.settings_live_url), ok, message)
                        }
                    } else {
                        reportConfig(1, resContext.getString(R.string.settings_live_url), false, resContext.getString(R.string.settings_no_config_url))
                    }
                },
            )
            }
            item(span = { GridItemSpan(2) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                title = stringResource(R.string.settings_wall_url),
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
                            reportConfig(2, resContext.getString(R.string.settings_wall_url), ok, message)
                        }
                    } else {
                        reportConfig(2, resContext.getString(R.string.settings_wall_url), false, resContext.getString(R.string.settings_no_config_url))
                    }
                },
            )
            }
            item(span = { GridItemSpan(2) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                title = stringResource(R.string.settings_danmaku_api),
                value = snapshot.danmakuApi.ifBlank { stringResource(R.string.settings_danmaku_api_default) },
                loading = reloadingDanmaku,
                initialFocus = danmakuApiFocus,
                onEdit = {
                    openConfig(3, snapshot.danmakuApi)
                },
                onReload = {
                    reloadingDanmaku = true
                    dataSource.setDanmakuApi(snapshot.danmakuApi)
                    snapshot = dataSource.load()
                    reloadingDanmaku = false
                },
            )
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_appearance))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val languageTags = com.onefilm.android.tv.compose.bridge.LanguageBridge.tags()
                val languageCurrent = com.onefilm.android.tv.compose.bridge.LanguageBridge.current()
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_language_title),
                    subtitle = com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf(languageCurrent),
                    options = languageTags.map {
                        com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf(it) to (it == languageCurrent)
                    },
                ) { index ->
                    languageTags.getOrNull(index)?.let {
                        com.onefilm.android.tv.compose.bridge.LanguageBridge.apply(it)
                    }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_theme_title),
                    subtitle = stringResource(R.string.settings_theme_default_hint),
                    options = listOf(
                        stringResource(R.string.settings_theme_light) to (snapshot.themeMode == 0),
                        stringResource(R.string.settings_theme_dark) to (snapshot.themeMode == 1),
                        stringResource(R.string.settings_theme_system) to (snapshot.themeMode == 2),
                    ),
                ) { index ->
                    refresh { setThemeMode(index) }
                    ThemeController.apply(index)
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_wall_switch), snapshot.wallpaperLabel) { refresh { cycleWallpaper() } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow("DoH", snapshot.dohLabel.ifBlank { stringResource(R.string.settings_follow_config) }) { refresh { cycleDoh() } }
            }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_incognito), stringResource(R.string.settings_no_history), snapshot.incognito) {
                    refresh { setIncognito(it) }
                }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_subtitle))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_subtitle_position),
                    subtitle = snapshot.subtitlePosition.toInt().toString(),
                    options = listOf(-5f, 0f, 5f, 10f, 20f).map {
                        it.toInt().toString() to (snapshot.subtitlePosition == it)
                    },
                ) { index -> refresh { setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_subtitle_color), snapshot.subtitleColorLabel) { refresh { cycleSubtitleColor() } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_subtitle_outline),
                    options = listOf(0f, 1f, 2f, 4f).map {
                        "%.0f".format(it) to (snapshot.subtitleEdgeWidth == it)
                    },
                ) { index -> refresh { setSubtitleEdgeWidth(listOf(0f, 1f, 2f, 4f)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_subtitle_size),
                    subtitle = "%.1fx".format(snapshot.subtitleScale),
                    options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                        "%.1fx".format(it) to (snapshot.subtitleScale == it)
                    },
                ) { index -> refresh { setSubtitleScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_subtitle_style),
                    subtitle = stringResource(R.string.settings_style_choices),
                    options = listOf(
                        stringResource(R.string.settings_original) to (snapshot.subtitleStyle == 0),
                        stringResource(R.string.settings_system) to (snapshot.subtitleStyle == 1),
                        stringResource(R.string.settings_custom) to (snapshot.subtitleStyle == 2),
                    ),
                ) { index -> refresh { setSubtitleStyle(index) } }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_danmaku))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_danmaku_show), stringResource(R.string.settings_load_danmaku), snapshot.danmakuShow) {
                    refresh { setDanmakuShow(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_danmaku_load), stringResource(R.string.settings_danmaku_load_desc), snapshot.danmakuLoad) {
                    refresh { setDanmakuLoad(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_danmaku_auto), stringResource(R.string.settings_danmaku_auto_desc), snapshot.danmakuAuto) {
                    refresh { setDanmakuAuto(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_size),
                    options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                        "%.1fx".format(it) to (snapshot.danmakuScale == it)
                    },
                ) { index -> refresh { setDanmakuScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_opacity),
                    options = listOf(0f, 0.25f, 0.5f, 0.7f).map {
                        "${(it * 100).toInt()}%" to (snapshot.danmakuTransparency == it)
                    },
                ) { index -> refresh { setDanmakuTransparency(listOf(0f, 0.25f, 0.5f, 0.7f)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_speed),
                    options = listOf(6000L, 8000L, 10000L).map {
                        "${it / 1000}s" to (snapshot.danmakuDurationMs == it)
                    },
                ) { index -> refresh { setDanmakuDuration(listOf(6000L, 8000L, 10000L)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_scroll_lines),
                    options = listOf(0, 4, 8, 12).map {
                        (if (it == 0) stringResource(R.string.settings_danmaku_auto_rows) else stringResource(R.string.settings_rows, it)) to (snapshot.danmakuLines == it)
                    },
                ) { index -> refresh { setDanmakuLines(listOf(0, 4, 8, 12)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_style),
                    options = listOf(
                        stringResource(R.string.settings_danmaku_style_stroke) to (snapshot.danmakuStyle == 1),
                        stringResource(R.string.settings_danmaku_style_shadow) to (snapshot.danmakuStyle == 2),
                        stringResource(R.string.settings_danmaku_style_projection) to (snapshot.danmakuStyle == 3),
                        stringResource(R.string.settings_danmaku_style_none) to (snapshot.danmakuStyle == 0),
                    ),
                ) { index -> refresh { setDanmakuStyle(listOf(1, 2, 3, 0)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_color),
                    options = listOf(
                        stringResource(R.string.settings_danmaku_color_default) to (snapshot.danmakuColorMode == 0),
                        stringResource(R.string.settings_danmaku_color_override) to (snapshot.danmakuColorMode == 1),
                        stringResource(R.string.settings_danmaku_color_random) to (snapshot.danmakuColorMode == 2),
                        stringResource(R.string.settings_danmaku_color_colorful) to (snapshot.danmakuColorMode == 3),
                        stringResource(R.string.settings_danmaku_color_gradient) to (snapshot.danmakuColorMode == 4),
                    ),
                ) { index -> refresh { setDanmakuColorMode(index) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_danmaku_bold), "", snapshot.danmakuBold) {
                    refresh { setDanmakuBold(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_top_lines),
                    options = listOf(0, 1, 2, 3, 5).map {
                        (if (it == 0) stringResource(R.string.settings_danmaku_auto_rows) else stringResource(R.string.settings_rows, it)) to (snapshot.danmakuTopLines == it)
                    },
                ) { index -> refresh { setDanmakuTopLines(listOf(0, 1, 2, 3, 5)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_bottom_lines),
                    options = listOf(0, 1, 2, 3, 5).map {
                        (if (it == 0) stringResource(R.string.settings_danmaku_auto_rows) else stringResource(R.string.settings_rows, it)) to (snapshot.danmakuBottomLines == it)
                    },
                ) { index -> refresh { setDanmakuBottomLines(listOf(0, 1, 2, 3, 5)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_time_offset),
                    options = listOf(-2000L, -1000L, 0L, 1000L, 2000L).map {
                        (if (it > 0) "+" else "") + "${it / 1000}s" to (snapshot.danmakuTimeOffsetMs == it)
                    },
                ) { index -> refresh { setDanmakuTimeOffsetMs(listOf(-2000L, -1000L, 0L, 1000L, 2000L)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_danmaku_max_on_screen),
                    options = listOf(50, 100, 150, 200, 300).map { "$it" to (snapshot.danmakuMaxOnScreen == it) },
                ) { index -> refresh { setDanmakuMaxOnScreen(listOf(50, 100, 150, 200, 300)[index]) } }
            }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                ActionRow(
                    stringResource(R.string.settings_danmaku_block),
                    snapshot.danmakuBlockWords.ifBlank { stringResource(R.string.settings_not_set) },
                    modifier = Modifier.focusRequester(blockWordsFocus),
                ) {
                    blockWordsDraft = snapshot.danmakuBlockWords
                    panelPage = 2
                }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_playback))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(
                    stringResource(R.string.settings_adblock),
                    stringResource(R.string.settings_adblock_desc),
                    snapshot.adblock,
                ) { enabled -> refresh { setAdblock(enabled) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_player_engine),
                    subtitle = stringResource(R.string.settings_mpv_legacy),
                    options = listOf("Exo" to !snapshot.engineMpv, "MPV" to snapshot.engineMpv),
                ) { index -> refresh { setEngine(index == 1) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_background_play),
                    subtitle = stringResource(R.string.settings_home_behavior),
                    options = listOf(
                        stringResource(R.string.settings_off) to (snapshot.background == 0),
                        stringResource(R.string.settings_background) to (snapshot.background == 1),
                        "PiP" to (snapshot.background == 2),
                    ),
                ) { index -> refresh { setBackground(index) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_buffer),
                    subtitle = stringResource(R.string.settings_seconds, snapshot.bufferSeconds),
                    options = listOf(1, 2, 5, 10).map {
                        "${it}s" to (snapshot.bufferSeconds == it)
                    },
                ) { index -> refresh { setBufferSeconds(listOf(1, 2, 5, 10)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_render_mode),
                    subtitle = "Surface / TextureView",
                    options = listOf("Surface" to !snapshot.renderTexture, "Texture" to snapshot.renderTexture),
                ) { index -> refresh { setRenderTexture(index == 1) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_tunnel), stringResource(R.string.settings_need_surface), snapshot.tunnel) { refresh { setTunnel(it) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(
                    title = stringResource(R.string.settings_dv_output),
                    value = snapshot.dvPolicyLabel,
                ) { refresh { cycleDolbyVisionPolicy() } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_play_timeout), stringResource(R.string.settings_seconds, snapshot.playTimeoutSeconds)) { refresh { cyclePlayTimeout() } }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_audio))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_audio_passthrough), stringResource(R.string.settings_hdmi_passthrough), snapshot.audioPassThrough) {
                    refresh { setAudioPassThrough(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_audio_soft), stringResource(R.string.settings_soft_audio_hint), snapshot.audioPrefer) {
                    refresh { setAudioPrefer(it) }
                }
            }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_video_soft), stringResource(R.string.settings_soft_video_hint), snapshot.videoPrefer) {
                    refresh { setVideoPrefer(it) }
                }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_search))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_search_threads),
                    subtitle = stringResource(R.string.settings_threads_hint),
                    options = SEARCH_THREAD_OPTIONS.map { "$it" to (snapshot.searchThreads == it) },
                ) { index ->
                    refresh { setSearchThreads(SEARCH_THREAD_OPTIONS[index]) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val sites = remember(snapshot.configUrl, showSearchSites) { dataSource.searchSites() }
                val enabledCount = sites.count { it.enabled }
                ActionRow(
                    title = stringResource(R.string.settings_search_sites),
                    value = when {
                        sites.isEmpty() -> stringResource(R.string.settings_not_loaded)
                        enabledCount == sites.size -> stringResource(R.string.settings_all_count, sites.size)
                        else -> "$enabledCount / ${sites.size}"
                    },
                ) { showSearchSites = true }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_enable_preload), stringResource(R.string.settings_legacy_preload_hint), snapshot.preloadEnabled) {
                    refresh { setPreloadEnabled(it) }
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_preload_next), stringResource(R.string.settings_exo_only), snapshot.preloadNext) {
                    refresh { setPreloadNext(it) }
                }
            }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_preload_threads),
                    options = (1..4).map { "$it" to (snapshot.preloadThreads == it) },
                ) { index -> refresh { setPreloadThreads(index + 1) } }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_storage))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_cache_size),
                    options = listOf(128, 256, 512, 1024).map {
                        "$it MB" to (snapshot.preloadSizeMb == it)
                    },
                ) { index -> refresh { setPreloadSizeMb(listOf(128, 256, 512, 1024)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_cache_days),
                    options = listOf(7, 30, 60, 90).map {
                        stringResource(R.string.settings_days, it) to (snapshot.cacheDays == it)
                    },
                ) { index -> refresh { setCacheDays(listOf(7, 30, 60, 90)[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_cache_clear), snapshot.cacheSizeLabel) {
                    showClearCacheConfirm = true
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val context = LocalContext.current
                ActionRow(stringResource(R.string.settings_export_diag), stringResource(R.string.settings_write_download)) {
                    val path = dataSource.exportDiagnostics()
                    val message = if (path.isBlank()) resContext.getString(R.string.settings_export_failed) else resContext.getString(R.string.settings_exported, path)
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_reset_ua), stringResource(R.string.settings_clear_custom)) { refresh { resetUa() } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(
                    stringResource(R.string.settings_force_update),
                    stringResource(R.string.settings_force_update_hint),
                    snapshot.forceUpdate,
                ) { refresh { setForceUpdate(it) } }
            }
            }

            item(span = { GridItemSpan(2) }) {
            SettingsGroupTitle(stringResource(R.string.settings_about))
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                InfoRow(stringResource(R.string.settings_version_title), "${snapshot.version} · GPL-3.0")
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                InfoRow(
                    stringResource(R.string.settings_latest_version),
                    latestVersion?.takeIf { it.isNotBlank() } ?: "—",
                )
            }
            }
            item(span = { GridItemSpan(2) }) {
                if (showContact) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(contactQrBringIntoView)
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.telegram_qr),
                            contentDescription = stringResource(R.string.settings_contact_qr_desc),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(220.dp),
                        )
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
            SettingsPanelRow {
                ActionRow(
                    stringResource(R.string.settings_contact_us),
                    stringResource(R.string.settings_contact_us_desc),
                ) { showContact = !showContact }
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
            width = 380.dp,
            padding = 12.dp,
        ) {
            Text(stringResource(R.string.settings_clear_cache_confirm), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.settings_clear_cache_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                SettingsButton(text = stringResource(R.string.settings_cancel), onClick = { showClearCacheConfirm = false })
                SettingsButton(
                    text = stringResource(R.string.settings_clear),
                    onClick = {
                        dataSource.clearCache()
                        snapshot = dataSource.load().copy(cacheSizeLabel = resContext.getString(R.string.settings_calculating))
                        showClearCacheConfirm = false
                        scope.launch {
                            val label = runCatching { dataSource.refreshCacheSize() }.getOrNull().orEmpty()
                            if (label.isNotBlank()) snapshot = snapshot.copy(cacheSizeLabel = label)
                        }
                    },
                    primary = true,
                )
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
            width = 360.dp,
            padding = 12.dp,
        ) {
            Text(
                text = if (configMessageSuccess) stringResource(R.string.settings_loaded) else stringResource(R.string.settings_load_failed),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                fontSize = 12.sp,
                color = if (configMessageSuccess) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                SettingsButton(
                    text = stringResource(R.string.settings_got_it),
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
            SettingsButton(text = stringResource(R.string.settings_back), onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(
                when (type) {
                    0 -> stringResource(R.string.settings_vod_url)
                    1 -> stringResource(R.string.settings_live_url)
                    2 -> stringResource(R.string.settings_wall_url)
                    else -> stringResource(R.string.settings_danmaku_api)
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (type != 3) {
            CompactTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.settings_line_name),
                modifier = Modifier.fillMaxWidth().focusRequester(firstFieldFocus),
            )
        }
        CompactTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = stringResource(R.string.settings_config_url),
            modifier = Modifier.fillMaxWidth().then(
                if (type == 3) Modifier.focusRequester(firstFieldFocus) else Modifier,
            ),
        )
        qrBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.settings_config_qr),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(160.dp).align(Alignment.CenterHorizontally),
            )
            Text(
                stringResource(R.string.settings_scan_hint),
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
                    text = if (loading) stringResource(R.string.settings_saving) else stringResource(R.string.settings_save),
                    onClick = onSave,
                    primary = true,
                    enabled = !loading && url.isNotBlank(),
                )
                SettingsButton(
                    text = stringResource(R.string.settings_cancel),
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
    TvTagButton(text = text, enabled = enabled, onClick = onClick)
}

@Composable
private fun BlockWordsEditPanel(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { fieldFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(text = stringResource(R.string.settings_back), onClick = onBack)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.settings_danmaku_block), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        CompactTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.settings_keywords_hint),
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
        )
        Text(
            stringResource(R.string.settings_keywords_hint),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(text = stringResource(R.string.settings_save), onClick = onSave, primary = true)
            SettingsButton(text = stringResource(R.string.settings_cancel), onClick = onBack)
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
        modifier = Modifier.padding(start = 2.dp, top = 3.dp),
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
            .heightIn(min = 34.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
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
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
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
                .width(72.dp)
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
        Text(title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                value.ifBlank { stringResource(R.string.settings_not_configured) },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text("›", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfigLoadButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvTagButton(
        text = if (loading) stringResource(R.string.settings_loading) else stringResource(R.string.settings_load),
        modifier = modifier,
        enabled = enabled && !loading,
        onClick = onClick,
    )
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
    padding: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
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
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
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
    val selectedLabel = options.firstOrNull { it.second }?.first ?: stringResource(R.string.settings_not_selected)
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
                .height(34.dp)
                .focusRequester(rowFocus)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 2.dp),
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
            stringResource(R.string.settings_off) to !checked,
            stringResource(R.string.settings_on) to checked,
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
    val selectedLabel = options.firstOrNull { it.second }?.first ?: stringResource(R.string.settings_not_selected)
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(34.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 2.dp),
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
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
                        stringResource(R.string.settings_search_sites),
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.settings_selected_count, states.count { it.value }, sites.size),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    DialogAction(stringResource(R.string.settings_select_all)) {
                        sites.forEach { states[it.key] = true }
                        onAll(true)
                    }
                    Spacer(Modifier.width(8.dp))
                    DialogAction(stringResource(R.string.settings_select_none)) {
                        sites.forEach { states[it.key] = false }
                        onAll(false)
                    }
                    Spacer(Modifier.width(8.dp))
                    DialogAction(stringResource(R.string.settings_done), onDismiss)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.settings_search_sites_hint),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (sites.isEmpty()) {
                    Text(
                        stringResource(R.string.settings_no_search_site),
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
    TvTagButton(text = label, onClick = onClick)
}
