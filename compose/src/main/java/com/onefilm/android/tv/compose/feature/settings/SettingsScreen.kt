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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.design.component.TvTagButton
import com.onefilm.android.tv.compose.design.component.PanTextField
import com.onefilm.android.tv.compose.bridge.ServerEventBridge
import com.onefilm.android.tv.compose.bridge.SEARCH_THREAD_OPTIONS
import com.onefilm.android.tv.compose.bridge.SearchSiteItem
import com.onefilm.android.tv.compose.design.PosterStyleController
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
    /** 当前接口类型下已保存的线路，以及所属仓库的线路（手机/ TV 共享同一套设置 UI）。 */
    var editLines by remember { mutableStateOf<List<ConfigLine>>(emptyList()) }
    var editDepotLines by remember { mutableStateOf<List<ConfigLine>>(emptyList()) }
    var editDepotTitle by remember { mutableStateOf("") }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showSearchSites by remember { mutableStateOf(false) }
    var showPosterStyleHint by remember { mutableStateOf(false) }
    var showContact by remember { mutableStateOf(false) }
    var reloadingDanmaku by remember { mutableStateOf(false) }
    var panelPage by remember { mutableStateOf(0) }
    var uaDraft by remember { mutableStateOf("") }
    /** 进入 UA 编辑页时记录的列表下标，返回时用它把该行滚回可视区域。 */
    var uaRowIndex by remember { mutableStateOf(-1) }
    /** 同上，弹幕屏蔽词行。 */
    var blockWordsRowIndex by remember { mutableStateOf(-1) }
    // AI 字幕（全部设置项直接内联在主面板，不再有二级页）
    val aiBridge = ComposeDependencies.aiSubtitle
    val aiModelBridge = ComposeDependencies.aiModel
    var aiTranslation by remember { mutableStateOf(aiBridge?.translationConfig()) }
    var aiTesting by remember { mutableStateOf(false) }
    var aiTestResult by remember { mutableStateOf<String?>(null) }
    var aiInstalled by remember { mutableStateOf(aiModelBridge?.isInstalled() ?: false) }
    var aiDownloadError by remember { mutableStateOf<String?>(null) }
    /** 设置分组展开状态：默认全部收起（key = 组序号）。 */
    val settingsExpanded = remember { mutableStateMapOf<Int, Boolean>() }
    // 识别语种（P6）：zh/en/de/fr/es，切换后按新语种加载模型
    var aiLanguage by remember { mutableStateOf(aiBridge?.languageCode() ?: "zh") }
    val aiLanguageOptions = remember { runCatching { aiBridge?.languageOptions() }.getOrNull().orEmpty() }
    val aiLanguageLabel = aiLanguageOptions.firstOrNull { it.code == aiLanguage }?.label ?: "普通话"
    // AI 字幕显示样式（设置面板与播放器弹窗共用 subtitleStyleState）
    val aiStyle = aiBridge?.subtitleStyleState?.value ?: com.onefilm.android.tv.compose.bridge.AiSubtitleStyle()
    fun applyAiStyle(block: (com.onefilm.android.tv.compose.bridge.AiSubtitleStyle) -> com.onefilm.android.tv.compose.bridge.AiSubtitleStyle) {
        val bridge = aiBridge ?: return
        bridge.setSubtitleStyle(block(bridge.subtitleStyle()))
    }
    // AI 翻译端点第二屏（MTranServer / 大模型）的草稿
    var aiEdName by remember { mutableStateOf("") }
    var aiEdUrl by remember { mutableStateOf("") }
    var aiEdKey by remember { mutableStateOf("") }
    var aiEdModel by remember { mutableStateOf("") }
    var aiEdError by remember { mutableStateOf<String?>(null) }
    var aiEdLoading by remember { mutableStateOf(false) }
    val aiMtranTitle = aiTranslation?.mtranName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_ai_mtran_title)
    val aiLlmTitle = aiTranslation?.llmName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.settings_ai_llm_title)
    var latestVersion by remember { mutableStateOf<String?>(null) }
    var configMessage by remember { mutableStateOf<String?>(null) }
    var configMessageSuccess by remember { mutableStateOf(true) }
    var configMessageTitle by remember { mutableStateOf<String?>(null) }
    var messageType by remember { mutableStateOf(0) }
    val configRowFocus = remember { List(3) { FocusRequester() } }
    /** 「MTranServer字幕接口」「AI字幕模型接口」两行（messageType 4/5 用）。 */
    val aiEndpointFocus = remember { List(2) { FocusRequester() } }
    val danmakuApiFocus = remember { FocusRequester() }
    val blockWordsFocus = remember { FocusRequester() }
    val uaRowFocus = remember { FocusRequester() }
    val contactQrBringIntoView = remember { BringIntoViewRequester() }
    val listState = rememberLazyGridState()
    val firstSettingFocus = remember { FocusRequester() }
    val editFieldFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val resContext = LocalContext.current
    /**
     * 从二级页回到设置列表后，把目标行滚回可视区域再请求焦点。
     *
     * LazyVerticalGrid 只组合可视区域内的 item：列表回顶时靠后的行（自定义 UA、屏蔽词）
     * 不在组合树里，FocusRequester 没有附着，requestFocus() 会抛 IllegalStateException
     * 并被 runCatching 吞掉，表现就是「返回/保存后焦点不见了」。这里按进入编辑页时
     * 记录的下标滚回去，并在网格重新布局完成前重试几次，与接口行/弹幕行返回时的处理一致。
     */
    fun restoreRowFocus(index: Int, requester: FocusRequester) {
        scope.launch {
            delay(150)
            if (index >= 0) runCatching { listState.scrollToItem(index) }
            delay(60)
            repeat(6) {
                if (runCatching { requester.requestFocus() }.isSuccess) return@launch
                delay(60)
            }
        }
    }
    /**
     * 按消息/配置类型把焦点还给对应的设置行。
     *
     * <p>三种关闭路径（弹窗「知道了」、弹窗的返回/点外部、二屏返回）都必须走这里，
     * 否则会出现「只有某一条路能恢复焦点」的问题。
     */
    fun restoreConfigRowFocus(type: Int) {
        when (type) {
            3 -> scope.launch {
                delay(150)
                runCatching { danmakuApiFocus.requestFocus() }
            }
            4, 5 -> scope.launch {
                // 与点播/直播/壁纸/弹幕完全一致：只重新要焦点，不滚动列表
                delay(150)
                runCatching { aiEndpointFocus[type - 4].requestFocus() }
            }
            else -> scope.launch {
                delay(150)
                configRowFocus.getOrNull(type)?.let { runCatching { it.requestFocus() } }
            }
        }
    }
    BackHandler(enabled = panelMode) {
        when {
            // 系统返回与页内「返回」按钮走同一条恢复路径，否则 UA / 屏蔽词行会丢焦点。
            panelPage == 4 -> {
                panelPage = 0
                restoreRowFocus(uaRowIndex, uaRowFocus)
            }
            panelPage == 2 -> {
                panelPage = 0
                restoreRowFocus(blockWordsRowIndex, blockWordsFocus)
            }
            panelPage == 6 || panelPage == 7 -> {
                val type = if (panelPage == 6) 4 else 5
                panelPage = 0
                restoreConfigRowFocus(type)
            }
            panelPage != 0 -> panelPage = 0
            else -> onRequestClose()
        }
    }
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
            if (type in configRowFocus.indices) {
                runCatching { listState.scrollToItem(type + 1) }
                delay(60)
                runCatching { configRowFocus[type].requestFocus() }
            }
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
    // 分组默认收起时，必须主动把焦点给第 1 个标题，否则 TV 遥控器第一次按键无处落脚
    val touchDevice = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(panelMode) {
        if (!panelMode) return@LaunchedEffect
        if (touchDevice) return@LaunchedEffect
        repeat(6) {
            delay(120)
            if (runCatching { firstSettingFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }

    LaunchedEffect(Unit) {
        ServerEventBridge.settings.collect { pushed ->
            val text = pushed.text.trim()
            when {
                pushed.name.equals("danmaku", true) || pushed.name == "弹幕" -> {
                    dataSource.setDanmakuApi(text)
                    snapshot = dataSource.load()
                }
                // AI 翻译端点：网页扫码推送（见 assets/index.html 的「AI 翻译」区）
                pushed.name == "mtran_url" || pushed.name == "mtran_key" ||
                    pushed.name == "llm_url" || pushed.name == "llm_key" ||
                    pushed.name == "llm_model" -> {
                    aiBridge?.let { bridge ->
                        when (pushed.name) {
                            "mtran_url" -> bridge.setMTranUrl(text)
                            "mtran_key" -> bridge.setMTranToken(text)
                            "llm_url" -> bridge.setApiBaseUrl(text)
                            "llm_key" -> bridge.setApiKey(text)
                            "llm_model" -> bridge.setApiModel(text)
                        }
                        aiTranslation = bridge.translationConfig()
                        // 若正在对应的第二屏，同步草稿，避免被旧值覆盖
                        when (pushed.name) {
                            "mtran_url" -> if (panelPage == 6) aiEdUrl = text
                            "mtran_key" -> if (panelPage == 6) aiEdKey = text
                            "llm_url" -> if (panelPage == 7) aiEdUrl = text
                            "llm_key" -> if (panelPage == 7) aiEdKey = text
                            "llm_model" -> if (panelPage == 7) aiEdModel = text
                        }
                    }
                }
                else -> {
                    editName = pushed.name
                    editUrl = pushed.text
                    configError = null
                }
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
        editLines = runCatching { dataSource.configLines(type) }.getOrDefault(emptyList())
        editDepotLines = if (type == 0) {
            runCatching { dataSource.depotLines() }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        editDepotTitle = runCatching { dataSource.depotName() }.getOrDefault("")
        configError = null
        qrBitmap = dataSource.qrBitmap(dataSource.configPushUrl(), 720)
        panelPage = 1
    }

    /** 打开 AI 翻译端点第二屏（6 = MTranServer，7 = 大模型）。 */
    fun openAiEndpoint(page: Int) {
        val cfg = aiBridge?.translationConfig() ?: return
        aiEdName = if (page == 6) cfg.mtranName else cfg.llmName
        aiEdUrl = if (page == 6) cfg.mtranUrl else cfg.baseUrl
        aiEdKey = if (page == 6) cfg.mtranToken else cfg.apiKey
        aiEdModel = if (page == 6) "" else cfg.model
        aiEdError = null
        qrBitmap = dataSource.qrBitmap(dataSource.configPushUrl(), 720)
        panelPage = page
    }

    /** 把第二屏草稿写回设置。 */
    fun applyAiEndpoint(page: Int): Boolean {
        val bridge = aiBridge ?: return false
        if (page == 6) {
            bridge.setMTranName(aiEdName.trim())
            bridge.setMTranUrl(aiEdUrl.trim())
            bridge.setMTranToken(aiEdKey.trim())
        } else {
            bridge.setApiName(aiEdName.trim())
            bridge.setApiBaseUrl(aiEdUrl.trim())
            bridge.setApiKey(aiEdKey.trim())
            bridge.setApiModel(aiEdModel.trim())
        }
        aiTranslation = bridge.translationConfig()
        return true
    }

    fun reportConfig(type: Int, label: String, ok: Boolean, message: String, title: String? = null) {
        messageType = type
        configMessageSuccess = ok
        configMessageTitle = title
        configMessage = if (ok) {
            message.ifBlank { resContext.getString(R.string.settings_load_ok, label) }
        } else {
            resContext.getString(R.string.settings_load_fail, label, message.ifBlank { resContext.getString(R.string.settings_check_url) })
        }
    }

    /** 行尾「加载」：用已保存的端点直接测连接（不改变当前选中的翻译服务）。 */
    fun testAiEndpoint(page: Int) {
        val bridge = aiBridge ?: return
        if (reloadingType != null) return
        val idx = if (page == 6) 4 else 5
        reloadingType = idx
        bridge.testTranslationProvider(if (page == 6) 1 else 2) { ok, detail ->
            reloadingType = null
            reportConfig(
                idx,
                if (page == 6) aiMtranTitle else aiLlmTitle,
                ok,
                detail,
                resContext.getString(R.string.settings_ai_test_title),
            )
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
                savedLines = if (type == 3) emptyList() else editLines,
                depotLines = if (type == 3) emptyList() else editDepotLines,
                depotTitle = editDepotTitle,
                onLoadSaved = { line ->
                    configLoading = true
                    configError = null
                    dataSource.loadConfig(type, line.url, line.name) { ok, message ->
                        configLoading = false
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                            editType = null
                            panelPage = 0
                            reportConfig(type, line.name.ifBlank { line.url }, true, message)
                            scope.launch {
                                delay(120)
                                runCatching { listState.scrollToItem(type + 1) }
                                delay(80)
                                runCatching { configRowFocus.getOrNull(type)?.requestFocus() }
                            }
                        } else {
                            configError = message
                        }
                    }
                },
                onDeleteSaved = { line ->
                    runCatching { dataSource.deleteConfigLine(type, line.url) }
                    editLines = runCatching { dataSource.configLines(type) }.getOrDefault(emptyList())
                    snapshot = dataSource.load()
                },
                onLoadDepot = { line ->
                    configLoading = true
                    configError = null
                    dataSource.selectDepotLine(line.url, line.name) { ok, message ->
                        configLoading = false
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                            editType = null
                            panelPage = 0
                            reportConfig(0, line.name.ifBlank { line.url }, true, message)
                            scope.launch {
                                delay(120)
                                runCatching { listState.scrollToItem(1) }
                                delay(80)
                                runCatching { configRowFocus[0].requestFocus() }
                            }
                        } else {
                            configError = message
                        }
                    }
                },
                onSave = {
                    if (type == 3) {
                        dataSource.setDanmakuApi(editUrl.trim())
                        // 地址变了：清掉源缓存与正在进行的请求，避免继续用旧服务端的结果
                        com.onefilm.android.tv.compose.feature.player.DanmakuController.invalidate()
                        snapshot = dataSource.load()
                        editType = null
                        panelPage = 0
                        reportConfig(
                            3,
                            resContext.getString(R.string.settings_danmaku_api),
                            true,
                            resContext.getString(R.string.settings_danmaku_saved),
                            resContext.getString(R.string.settings_saved),
                        )
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
                                    delay(120)
                                    runCatching { listState.scrollToItem(type + 1) }
                                    delay(80)
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
                            delay(160)
                            if (type == 3) {
                                runCatching { listState.scrollToItem(4) }
                                delay(80)
                                runCatching { danmakuApiFocus.requestFocus() }
                            } else if (type in configRowFocus.indices) {
                                runCatching { listState.scrollToItem(type + 1) }
                                delay(80)
                                runCatching { configRowFocus[type].requestFocus() }
                            }
                        }
                    }
                },
            )
            panelPage == 4 -> UaEditPanel(
                value = uaDraft,
                onValueChange = { uaDraft = it },
                onSave = {
                    dataSource.setUa(uaDraft)
                    snapshot = dataSource.load()
                    panelPage = 0
                    android.widget.Toast.makeText(
                        resContext,
                        resContext.getString(R.string.settings_saved),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    restoreRowFocus(uaRowIndex, uaRowFocus)
                },
                onBack = {
                    panelPage = 0
                    restoreRowFocus(uaRowIndex, uaRowFocus)
                },
            )
            panelPage == 2 -> BlockWordsEditPanel(
                value = blockWordsDraft,
                onValueChange = { blockWordsDraft = it },
                onSave = {
                    dataSource.setDanmakuBlockWords(blockWordsDraft.trim())
                    snapshot = dataSource.load()
                    panelPage = 0
                    restoreRowFocus(blockWordsRowIndex, blockWordsFocus)
                },
                onBack = {
                    panelPage = 0
                    restoreRowFocus(blockWordsRowIndex, blockWordsFocus)
                },
            )
            panelPage == 6 || panelPage == 7 -> {
                val aiPage = panelPage
                AiEndpointPanel(
                    title = if (aiPage == 6) aiMtranTitle else aiLlmTitle,
                    name = aiEdName,
                    onNameChange = { aiEdName = it },
                    url = aiEdUrl,
                    onUrlChange = { aiEdUrl = it },
                    urlPlaceholder = if (aiPage == 6) {
                        stringResource(R.string.settings_ai_mtran_url_hint)
                    } else {
                        stringResource(R.string.settings_ai_llm_url_hint)
                    },
                    key = aiEdKey,
                    onKeyChange = { aiEdKey = it },
                    keyPlaceholder = if (aiPage == 6) {
                        stringResource(R.string.settings_ai_mtran_key_hint)
                    } else {
                        stringResource(R.string.settings_ai_llm_key_hint)
                    },
                    model = if (aiPage == 6) null else aiEdModel,
                    onModelChange = if (aiPage == 6) {
                        null
                    } else {
                        { value: String -> aiEdModel = value }
                    },
                    modelPlaceholder = stringResource(R.string.settings_ai_llm_model_hint),
                    qrBitmap = qrBitmap,
                    error = aiEdError,
                    loading = aiEdLoading,
                    firstFieldFocus = editFieldFocus,
                    onSave = {
                        if (applyAiEndpoint(aiPage)) {
                            aiEdError = null
                            panelPage = 0
                            reportConfig(
                                if (aiPage == 6) 4 else 5,
                                if (aiPage == 6) aiMtranTitle else aiLlmTitle,
                                true,
                                resContext.getString(R.string.settings_ai_saved),
                                resContext.getString(R.string.settings_saved),
                            )
                        } else {
                            aiEdError = resContext.getString(R.string.settings_ai_unavailable)
                        }
                    },
                    onCancel = {
                        panelPage = 0
                        restoreConfigRowFocus(if (aiPage == 6) 4 else 5)
                    },
                )
            }
            else -> {
                // 与 TV 同一套 2 列设置布局。
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(
                1,
                stringResource(R.string.settings_network),
                settingsExpanded[1] == true,
                modifier = Modifier.focusRequester(firstSettingFocus),
            ) { settingsExpanded[1] = !(settingsExpanded[1] == true) }
            }
            if (settingsExpanded[1] == true) {
            item(span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                title = stringResource(R.string.settings_danmaku_api),
                value = snapshot.danmakuApi.ifBlank { stringResource(R.string.settings_danmaku_api_default) },
                loading = reloadingDanmaku,
                initialFocus = danmakuApiFocus,
                onEdit = {
                    openConfig(3, snapshot.danmakuApi)
                },
                onReload = {
                    if (!reloadingDanmaku) {
                        val target = snapshot.danmakuApi.trim()
                        if (target.isEmpty()) {
                            reportConfig(
                                3,
                                resContext.getString(R.string.settings_danmaku_api),
                                false,
                                resContext.getString(R.string.settings_danmaku_probe_empty),
                            )
                        } else {
                            reloadingDanmaku = true
                            scope.launch {
                                val result = runCatching { dataSource.probeDanmakuApi(target) }
                                    .getOrElse { error ->
                                        com.onefilm.android.tv.compose.bridge.DanmakuProbeResult(
                                            false,
                                            error.message.orEmpty(),
                                        )
                                    }
                                reloadingDanmaku = false
                                reportConfig(
                                    3,
                                    resContext.getString(R.string.settings_danmaku_api),
                                    result.ok,
                                    if (result.ok) {
                                        resContext.getString(R.string.settings_danmaku_probe_ok, result.detail)
                                    } else {
                                        resContext.getString(R.string.settings_danmaku_probe_fail, result.detail)
                                    },
                                )
                            }
                        }
                    }
                },
            )
            }
            // AI 翻译服务端点：与「点播接口」同一套（输入框 + 加载；点行进入第二屏）
            item(key = "settings-ai-mtran", span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                title = aiMtranTitle,
                value = aiTranslation?.mtranUrl.orEmpty(),
                loading = reloadingType == 4,
                initialFocus = aiEndpointFocus[0],
                onEdit = { openAiEndpoint(6) },
                onReload = { testAiEndpoint(6) },
            )
            }
            item(key = "settings-ai-llm", span = { GridItemSpan(maxLineSpan) }) {
            ConfigBlock(
                modifier = Modifier.fillMaxWidth().height(34.dp),
                title = aiLlmTitle,
                value = aiTranslation?.baseUrl.orEmpty(),
                loading = reloadingType == 5,
                initialFocus = aiEndpointFocus[1],
                onEdit = { openAiEndpoint(7) },
                onReload = { testAiEndpoint(7) },
            )
            }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(2, stringResource(R.string.settings_appearance), settingsExpanded[2] == true) { settingsExpanded[2] = !(settingsExpanded[2] == true) }
            }
            if (settingsExpanded[2] == true) {
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
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_poster_style_title),
                    subtitle = if (snapshot.posterStyle == 1) {
                        stringResource(R.string.settings_poster_style_horizontal)
                    } else {
                        stringResource(R.string.settings_poster_style_vertical)
                    },
                    options = listOf(
                        stringResource(R.string.settings_poster_style_vertical) to (snapshot.posterStyle == 0),
                        stringResource(R.string.settings_poster_style_horizontal) to (snapshot.posterStyle == 1),
                    ),
                ) { index ->
                    refresh { setPosterStyle(index) }
                    PosterStyleController.apply(index)
                    if (index == 1) showPosterStyleHint = true
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
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_incognito), stringResource(R.string.settings_no_history), snapshot.incognito) {
                    refresh { setIncognito(it) }
                }
            }
            }

            // AI 实时影视字幕同步翻译：全部设置项直接内联在主面板（与其它设置一致，不再进二级页）
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(3, stringResource(R.string.settings_ai_subtitle), settingsExpanded[3] == true) { settingsExpanded[3] = !(settingsExpanded[3] == true) }
            }
            if (settingsExpanded[3] == true) {
            item(key = "settings-ai-switch", span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val aiReady = runCatching { dataSource.aiSubtitleAvailable() }.getOrDefault(false)
                SwitchRow(
                    title = stringResource(R.string.settings_ai_subtitle_enabled),
                    subtitle = stringResource(R.string.settings_ai_subtitle_enabled_hint),
                    checked = snapshot.aiSubtitleEnabled && aiReady,
                ) { on ->
                    dataSource.setAiSubtitleEnabled(on)
                    snapshot = dataSource.load()
                }
            }
            }
            // 识别语言 + 识别模型：状态框 + 行尾独立按钮框（与「网络接口」一致）
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_language),
                    subtitle = aiLanguageLabel,
                    options = aiLanguageOptions.map { it.label to (it.code == aiLanguage) },
                ) { index ->
                    val option = aiLanguageOptions.getOrNull(index)
                    if (option != null && option.code != aiLanguage) {
                        aiLanguage = option.code
                        aiBridge?.setLanguage(option.code)
                        aiInstalled = aiModelBridge?.isInstalled() ?: false
                        aiDownloadError = null
                    }
                }
            }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsPanelRow(Modifier.weight(1f)) {
                    val downloadingModel = aiModelBridge?.downloading?.value ?: false
                    val modelText = when {
                        downloadingModel ->
                            aiLanguageLabel + " · 下载中 " + (aiModelBridge?.progressPct?.value ?: 0) + "%"
                        aiInstalled ->
                            aiLanguageLabel + " · 已安装 · %.1f MB".format((aiModelBridge?.installedBytes() ?: 0L) / 1048576.0)
                        else -> aiLanguageLabel + " · 未安装"
                    }
                    Text(
                        text = stringResource(R.string.settings_ai_subtitle_model) + "  " + modelText,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (aiModelBridge != null) {
                    val downloadingModel = aiModelBridge.downloading.value
                    // 始终是同一个 TvTagButton：只改文字/可用状态，避免被替换导致 TV 焦点丢失
                    TvTagButton(
                        text = when {
                            downloadingModel -> aiModelBridge.progressPct.value.toString() + "%"
                            aiInstalled -> stringResource(R.string.settings_ai_subtitle_delete)
                            else -> stringResource(R.string.settings_ai_subtitle_download)
                        },
                        enabled = !downloadingModel,
                        onClick = {
                            if (downloadingModel) return@TvTagButton
                            if (aiInstalled) {
                                aiModelBridge.delete()
                                aiInstalled = false
                            } else {
                                aiDownloadError = null
                                aiModelBridge.download(
                                    onProgress = { _, _ -> },
                                    onDone = { ok, message ->
                                        aiInstalled = ok
                                        aiDownloadError = message
                                    },
                                )
                            }
                        },
                    )
                }
            }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.settings_ai_subtitle_privacy),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val aiErr = aiDownloadError
                if (aiErr != null) {
                    Text(
                        stringResource(R.string.settings_ai_subtitle_failed, aiErr),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else if (aiModelBridge != null && !aiInstalled) {
                    Text(
                        stringResource(R.string.settings_ai_subtitle_unavailable),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            }
            val aiTr = aiTranslation
            if (aiBridge != null && aiTr != null) {
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(
                    title = stringResource(R.string.settings_ai_subtitle_translate),
                    subtitle = stringResource(R.string.settings_ai_subtitle_translate_hint),
                    checked = aiTr.enabled,
                ) { on ->
                    aiBridge.setTranslationEnabled(on)
                    aiTestResult = null
                    aiTranslation = aiBridge.translationConfig()
                }
            }
            }
            if (aiTr.enabled) {
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_subtitle_provider),
                    subtitle = aiProviderLabel(aiTr.provider),
                    options = listOf(
                        stringResource(R.string.settings_ai_subtitle_provider_default) to (aiTr.provider == 0),
                        stringResource(R.string.settings_ai_subtitle_provider_mtran) to (aiTr.provider == 1),
                        stringResource(R.string.settings_ai_subtitle_provider_openai) to (aiTr.provider == 2),
                        stringResource(R.string.settings_ai_subtitle_provider_tencent) to (aiTr.provider == 3),
                        stringResource(R.string.settings_ai_subtitle_provider_bing) to (aiTr.provider == 4),
                    ),
                ) { index ->
                    aiBridge.setTranslationProvider(index)
                    aiTestResult = null
                    aiTranslation = aiBridge.translationConfig()
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_subtitle_mode),
                    subtitle = if (aiTr.mode == 1) {
                        stringResource(R.string.settings_ai_subtitle_mode_translated)
                    } else {
                        stringResource(R.string.settings_ai_subtitle_mode_bilingual)
                    },
                    options = listOf(
                        stringResource(R.string.settings_ai_subtitle_mode_bilingual) to (aiTr.mode == 0),
                        stringResource(R.string.settings_ai_subtitle_mode_translated) to (aiTr.mode == 1),
                    ),
                ) { index ->
                    aiBridge.setSubtitleMode(index)
                    aiTranslation = aiBridge.translationConfig()
                }
            }
            }
            if (aiTr.provider != 0) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stringResource(R.string.settings_ai_endpoint_hint),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
            }
            if (aiTr.provider == 2) {
                    item(span = { GridItemSpan(1) }) {
                    SettingsPanelRow {
                        SwitchRow(
                            title = stringResource(R.string.settings_ai_subtitle_thinking),
                            subtitle = stringResource(R.string.settings_ai_subtitle_thinking_hint),
                            checked = aiTr.thinking,
                        ) { on ->
                            aiBridge.setThinkingEnabled(on)
                            aiTranslation = aiBridge.translationConfig()
                        }
                    }
                    }
                    item(span = { GridItemSpan(1) }) {
                    SettingsPanelRow {
                        SwitchRow(
                            title = stringResource(R.string.settings_ai_subtitle_context),
                            subtitle = stringResource(R.string.settings_ai_subtitle_context_hint),
                            checked = aiTr.context,
                        ) { on ->
                            aiBridge.setContextEnabled(on)
                            aiTranslation = aiBridge.translationConfig()
                        }
                    }
                    }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsPanelRow {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvTagButton(
                        text = if (aiTesting) {
                            stringResource(R.string.settings_ai_subtitle_testing)
                        } else {
                            stringResource(R.string.settings_ai_subtitle_test)
                        },
                        enabled = !aiTesting,
                        onClick = {
                            aiTesting = true
                            aiTestResult = null
                            aiBridge.testTranslation { ok, detail ->
                                aiTesting = false
                                aiTestResult = (if (ok) "✓ " else "✗ ") + detail
                            }
                        },
                    )
                    val result = aiTestResult
                    if (result != null) {
                        Text(
                            result,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = if (result.startsWith("✓")) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(R.string.settings_ai_subtitle_test_hint),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val positions = listOf(-40, -20, 0, 20, 40, 60)
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_position),
                    subtitle = (if (aiStyle.offsetDp > 0) "+${aiStyle.offsetDp}" else aiStyle.offsetDp.toString()) + " dp",
                    options = positions.map { (if (it > 0) "+$it" else it.toString()) to (aiStyle.offsetDp == it) },
                ) { index -> applyAiStyle { it.copy(offsetDp = positions[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_color),
                    subtitle = listOf(
                        stringResource(R.string.ai_color_white),
                        stringResource(R.string.ai_color_red),
                        stringResource(R.string.ai_color_green),
                        stringResource(R.string.ai_color_yellow),
                        stringResource(R.string.ai_color_random),
                    ).getOrElse(aiStyle.color) { "" },
                    options = listOf(
                        stringResource(R.string.ai_color_white) to (aiStyle.color == 0),
                        stringResource(R.string.ai_color_red) to (aiStyle.color == 1),
                        stringResource(R.string.ai_color_green) to (aiStyle.color == 2),
                        stringResource(R.string.ai_color_yellow) to (aiStyle.color == 3),
                        stringResource(R.string.ai_color_random) to (aiStyle.color == 4),
                    ),
                ) { index -> applyAiStyle { it.copy(color = index) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                val sizes = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f)
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_size),
                    subtitle = "%.1fx".format(aiStyle.sizeScale),
                    options = sizes.map { "%.1fx".format(it) to (kotlin.math.abs(aiStyle.sizeScale - it) < 0.01f) },
                ) { index -> applyAiStyle { it.copy(sizeScale = sizes[index]) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_stroke),
                    subtitle = listOf(
                        stringResource(R.string.ai_stroke_shadow),
                        stringResource(R.string.ai_stroke_outline),
                        stringResource(R.string.ai_stroke_none),
                    ).getOrElse(aiStyle.stroke) { "" },
                    options = listOf(
                        stringResource(R.string.ai_stroke_shadow) to (aiStyle.stroke == 0),
                        stringResource(R.string.ai_stroke_outline) to (aiStyle.stroke == 1),
                        stringResource(R.string.ai_stroke_none) to (aiStyle.stroke == 2),
                    ),
                ) { index -> applyAiStyle { it.copy(stroke = index) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_bold),
                    subtitle = if (aiStyle.bold) stringResource(R.string.settings_on)
                    else stringResource(R.string.settings_off),
                    options = listOf(
                        stringResource(R.string.settings_off) to !aiStyle.bold,
                        stringResource(R.string.settings_on) to aiStyle.bold,
                    ),
                ) { index -> applyAiStyle { it.copy(bold = index == 1) } }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_ai_style_font),
                    subtitle = listOf(
                        stringResource(R.string.ai_font_default),
                        stringResource(R.string.ai_font_round),
                        stringResource(R.string.ai_font_serif),
                    ).getOrElse(aiStyle.font) { "" },
                    options = listOf(
                        stringResource(R.string.ai_font_default) to (aiStyle.font == 0),
                        stringResource(R.string.ai_font_round) to (aiStyle.font == 1),
                        stringResource(R.string.ai_font_serif) to (aiStyle.font == 2),
                    ),
                ) { index -> applyAiStyle { it.copy(font = index) } }
            }
            }
            }
            }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(4, stringResource(R.string.settings_subtitle), settingsExpanded[4] == true) { settingsExpanded[4] = !(settingsExpanded[4] == true) }
            }
            if (settingsExpanded[4] == true) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
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

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(5, stringResource(R.string.settings_danmaku), settingsExpanded[5] == true) { settingsExpanded[5] = !(settingsExpanded[5] == true) }
            }
            if (settingsExpanded[5] == true) {
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
            item(key = "settings-block-words", span = { GridItemSpan(maxLineSpan) }) {
            SettingsPanelRow {
                ActionRow(
                    stringResource(R.string.settings_danmaku_block),
                    snapshot.danmakuBlockWords.ifBlank { stringResource(R.string.settings_not_set) },
                    modifier = Modifier.focusRequester(blockWordsFocus),
                ) {
                    // 记住本行在列表中的下标，返回时先用它滚回可视区域再要焦点。
                    blockWordsRowIndex = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "settings-block-words" }?.index ?: -1
                    blockWordsDraft = snapshot.danmakuBlockWords
                    panelPage = 2
                }
            }
            }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(6, stringResource(R.string.settings_playback), settingsExpanded[6] == true) { settingsExpanded[6] = !(settingsExpanded[6] == true) }
            }
            if (settingsExpanded[6] == true) {
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

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(7, stringResource(R.string.settings_audio), settingsExpanded[7] == true) { settingsExpanded[7] = !(settingsExpanded[7] == true) }
            }
            if (settingsExpanded[7] == true) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsPanelRow {
                SwitchRow(stringResource(R.string.settings_video_soft), stringResource(R.string.settings_soft_video_hint), snapshot.videoPrefer) {
                    refresh { setVideoPrefer(it) }
                }
            }
            }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(8, stringResource(R.string.settings_search), settingsExpanded[8] == true) { settingsExpanded[8] = !(settingsExpanded[8] == true) }
            }
            if (settingsExpanded[8] == true) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsPanelRow {
                ExpandableChoiceRow(
                    title = stringResource(R.string.settings_preload_threads),
                    options = (1..4).map { "$it" to (snapshot.preloadThreads == it) },
                ) { index -> refresh { setPreloadThreads(index + 1) } }
            }
            }

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(9, stringResource(R.string.settings_storage), settingsExpanded[9] == true) { settingsExpanded[9] = !(settingsExpanded[9] == true) }
            }
            if (settingsExpanded[9] == true) {
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
            item(key = "settings-ua", span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(
                    title = stringResource(R.string.settings_custom_ua),
                    value = snapshot.ua.ifBlank { stringResource(R.string.common_default) },
                    modifier = Modifier.focusRequester(uaRowFocus),
                ) {
                    // 记住本行在列表中的下标，返回时先用它滚回可视区域再要焦点。
                    uaRowIndex = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "settings-ua" }?.index ?: -1
                    uaDraft = snapshot.ua
                    panelPage = 4
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                ActionRow(stringResource(R.string.settings_reset_ua), stringResource(R.string.settings_clear_custom)) {
                    refresh { resetUa() }
                    android.widget.Toast.makeText(
                        resContext,
                        resContext.getString(R.string.settings_reset_ua_done),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            }
            item(span = { GridItemSpan(1) }) {
            SettingsPanelRow {
                SwitchRow(
                    stringResource(R.string.settings_update_check),
                    stringResource(R.string.settings_update_check_hint),
                    snapshot.updateCheck,
                ) { refresh { setUpdateCheck(it) } }
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

            }
            item(span = { GridItemSpan(maxLineSpan) }) {
            SettingsGroupTitle(10, stringResource(R.string.settings_about), settingsExpanded[10] == true) { settingsExpanded[10] = !(settingsExpanded[10] == true) }
            }
            if (settingsExpanded[10] == true) {
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
            item(span = { GridItemSpan(maxLineSpan) }) {
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
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
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

    if (showPosterStyleHint) {
        SettingsDialog(
            onDismiss = { showPosterStyleHint = false },
            width = 420.dp,
            padding = 16.dp,
        ) {
            Text(
                text = stringResource(R.string.settings_poster_style_hint),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }

    configMessage?.let { message ->
        SettingsDialog(
            onDismiss = {
                configMessage = null
                configMessageTitle = null
                restoreConfigRowFocus(messageType)
            },
            width = 360.dp,
            padding = 12.dp,
        ) {
            Text(
                text = configMessageTitle ?: if (configMessageSuccess) stringResource(R.string.settings_loaded) else stringResource(R.string.settings_load_failed),
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
                        restoreConfigRowFocus(messageType)
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
    savedLines: List<ConfigLine>,
    depotLines: List<ConfigLine>,
    depotTitle: String,
    onLoadSaved: (ConfigLine) -> Unit,
    onDeleteSaved: (ConfigLine) -> Unit,
    onLoadDepot: (ConfigLine) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    // 触屏设备不自动聚焦输入框，避免进入编辑页就弹出软键盘遮挡内容。
    val touch = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(Unit) {
        if (touch) return@LaunchedEffect
        delay(120)
        runCatching { firstFieldFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (depotLines.isNotEmpty()) {
            Text(
                depotTitle.ifBlank { stringResource(R.string.settings_depot_lines) },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            depotLines.forEach { line ->
                SavedLineRow(
                    name = line.name,
                    url = line.url,
                    enabled = !loading,
                    onLoad = { onLoadDepot(line) },
                )
            }
        }
        if (savedLines.isNotEmpty()) {
            Text(
                stringResource(R.string.settings_saved_lines),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            savedLines.forEach { line ->
                SavedLineRow(
                    name = line.name,
                    url = line.url,
                    enabled = !loading,
                    current = line.current,
                    onLoad = { onLoadSaved(line) },
                    onDelete = { onDeleteSaved(line) },
                )
            }
            Spacer(Modifier.height(2.dp))
        }
        if (type != 3) {
            ConfigTextField(
                value = name,
                onValueChange = onNameChange,
                placeholder = stringResource(R.string.settings_line_name),
                fieldModifier = Modifier.focusRequester(firstFieldFocus),
            )
        }
        ConfigTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = stringResource(R.string.settings_config_url),
            fieldModifier = if (type == 3) Modifier.focusRequester(firstFieldFocus) else Modifier,
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
                    text = stringResource(R.string.settings_back),
                    onClick = onBack,
                    enabled = !loading,
                )
                SettingsButton(
                    text = if (loading) stringResource(R.string.settings_saving) else stringResource(R.string.settings_save),
                    onClick = onSave,
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

/**
 * AI 翻译端点第二屏（MTranServer / 大模型）。
 *
 * <p>布局与「点播接口」第二屏一致：名称 / 地址 / 密钥（/ 模型）/ 二维码推送 / 返回·保存·取消。
 */
@Composable
private fun AiEndpointPanel(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    urlPlaceholder: String,
    key: String,
    onKeyChange: (String) -> Unit,
    keyPlaceholder: String,
    model: String?,
    onModelChange: ((String) -> Unit)?,
    modelPlaceholder: String,
    qrBitmap: android.graphics.Bitmap?,
    error: String?,
    loading: Boolean,
    firstFieldFocus: FocusRequester,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    // 与「点播接口」二屏一致：进入后把焦点放到第一个输入框，否则 TV 遥控器无处落脚
    val touch = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(Unit) {
        if (touch) return@LaunchedEffect
        delay(120)
        runCatching { firstFieldFocus.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        ConfigTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.settings_line_name),
            fieldModifier = Modifier.focusRequester(firstFieldFocus),
        )
        ConfigTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = urlPlaceholder,
        )
        ConfigTextField(
            value = key,
            onValueChange = onKeyChange,
            placeholder = keyPlaceholder,
            password = true,
        )
        if (model != null && onModelChange != null) {
            ConfigTextField(
                value = model,
                onValueChange = onModelChange,
                placeholder = modelPlaceholder,
            )
        }
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
                    text = stringResource(R.string.settings_back),
                    onClick = onCancel,
                    enabled = !loading,
                )
                SettingsButton(
                    text = if (loading) stringResource(R.string.settings_saving)
                    else stringResource(R.string.settings_save),
                    onClick = onSave,
                    enabled = !loading && url.isNotBlank(),
                )
                SettingsButton(
                    text = stringResource(R.string.settings_cancel),
                    onClick = onCancel,
                    enabled = !loading,
                )
            }
        }
    }
}

/** 接口/UA 编辑页的输入框：与设置列表里的设置项同一套背景、圆角、边框和聚焦态。 */
@Composable
private fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldModifier: Modifier = Modifier,
    password: Boolean = false,
) {
    SettingsPanelRow(Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = if (password) {
                PasswordVisualTransformation()
            } else {
                VisualTransformation.None
            },
            modifier = fieldModifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
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
}

/**
 * 已保存线路 / 仓库线路的一行：
 * 左侧是「载入」行（可聚焦，确定键载入），右侧是独立的「删除」按钮（同样可聚焦），
 * 与网络接口第一页的「加载」按钮保持一致的交互。
 */
@Composable
private fun SavedLineRow(
    name: String,
    url: String,
    enabled: Boolean,
    current: Boolean = false,
    onLoad: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsPanelRow(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // clickable 放在内容里（与其它设置项一致），外层的 onFocusChanged 才能观察到焦点并高亮边框。
                    .clickable(enabled = enabled, onClick = onLoad)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 名字与 URL 同一行：名字保留约 1/3 宽度，URL 占其余并截断。
                val hasName = name.isNotBlank() && name != url
                Text(
                    if (hasName) name else url,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(if (hasName) 0.38f else 1f),
                )
                if (hasName) {
                    Text(
                        url,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.62f).padding(start = 6.dp),
                    )
                }
            }
        }
        if (current) {
            // 「当前」只是状态标记，不可聚焦；占位宽度与删除按钮一致，保证各行对齐。
            Box(
                Modifier.width(72.dp).heightIn(min = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.common_current),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else if (onDelete != null) {
            TvTagButton(
                text = stringResource(R.string.common_delete),
                modifier = Modifier.width(72.dp),
                enabled = enabled,
                onClick = { onDelete() },
            )
        }
    }
}

@Composable
private fun UaEditPanel(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    val fieldFocus = remember { FocusRequester() }
    val touch = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(Unit) {
        if (touch) return@LaunchedEffect
        delay(150)
        runCatching { fieldFocus.requestFocus() }
    }
    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ConfigTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = stringResource(R.string.settings_custom_ua),
            fieldModifier = Modifier.focusRequester(fieldFocus),
        )
        Text(
            stringResource(R.string.settings_ua_hint),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton(text = stringResource(R.string.settings_back), onClick = onBack)
                SettingsButton(text = stringResource(R.string.settings_save), onClick = onSave)
                SettingsButton(text = stringResource(R.string.settings_cancel), onClick = onBack)
            }
        }
    }
}

@Composable
private fun aiProviderLabel(provider: Int): String = when (provider) {
    1 -> stringResource(R.string.settings_ai_subtitle_provider_mtran)
    2 -> stringResource(R.string.settings_ai_subtitle_provider_openai)
    3 -> stringResource(R.string.settings_ai_subtitle_provider_tencent)
    4 -> stringResource(R.string.settings_ai_subtitle_provider_bing)
    else -> stringResource(R.string.settings_ai_subtitle_provider_default)
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
    val touch = LocalConfiguration.current.screenWidthDp < 600
    LaunchedEffect(Unit) {
        if (touch) return@LaunchedEffect
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

/**
 * 设置分组标题：序号 + 名称，点击展开/收起该组（TV 遥控器可聚焦）。
 *
 * <p>字号 16sp（原 12sp）：分组标题要明显大于行内文字（13sp）才有层级感。
 */
@Composable
private fun SettingsGroupTitle(
    index: Int,
    title: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.hasFocus }
            .border(
                if (focused) 2.dp else 0.dp,
                if (focused) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$index、$title",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // 用 Canvas 画三角：某些 TV 固件字体没有 ▾/▸ 字形，文字箭头会显示成空白
        val arrowColor = MaterialTheme.colorScheme.primary
        androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
            val p = androidx.compose.ui.graphics.Path()
            if (expanded) {
                p.moveTo(0f, size.height * 0.30f)
                p.lineTo(size.width, size.height * 0.30f)
                p.lineTo(size.width * 0.5f, size.height * 0.80f)
            } else {
                p.moveTo(size.width * 0.30f, 0f)
                p.lineTo(size.width * 0.80f, size.height * 0.5f)
                p.lineTo(size.width * 0.30f, size.height)
            }
            p.close()
            drawPath(p, color = arrowColor)
        }
    }
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
                // 统一用纯色：之前的左右渐变会让两列底色不一致（左暗右白）。
                if (focused) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
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
