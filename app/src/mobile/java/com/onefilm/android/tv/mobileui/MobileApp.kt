package com.onefilm.android.tv.mobileui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.HomeFeedCache
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.UpdateInfo
import com.onefilm.android.tv.compose.feature.home.HomeSitePickerDialog
import com.onefilm.android.tv.compose.feature.home.HomeUiState
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.compose.feature.search.SearchSession
import com.onefilm.android.tv.compose.feature.search.SearchUiState
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen
import com.onefilm.android.tv.mobileui.component.MobileHorizontalGrid
import com.onefilm.android.tv.mobileui.component.MobileRemoteImage
import com.onefilm.android.tv.mobileui.player.MobilePlayerScreen
import com.onefilm.android.tv.ui.activity.LiveActivity
import com.onefilm.android.tv.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MobileTab(val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.mobile_tab_home, Icons.Outlined.Home),
    LIBRARY(R.string.mobile_tab_continue, Icons.Outlined.History),
    LIVE(R.string.nav_live, Icons.Outlined.LiveTv),
    SETTINGS(R.string.nav_setting, Icons.Outlined.Settings),
}

/** 手机端页面栈：列表 → 详情 → 播放器。 */
sealed interface MobileScreen {
    data object Root : MobileScreen
    data class SearchResults(val keyword: String) : MobileScreen
    data class Detail(
        val key: String,
        val id: String,
        val title: String,
        val poster: String?,
        val returnTo: MobileScreen? = null,
    ) : MobileScreen
    data class Player(val detail: Detail) : MobileScreen

    /** 网盘直接播放：不经过详情页，返回时回到进入前的页面（继续观看 Tab）。 */
    data class NetdiskPlayer(val returnTo: MobileScreen = Root) : MobileScreen
}

@Composable
fun MobileApp() {
    val context = LocalContext.current
    var homeState by remember {
        mutableStateOf<HomeUiState>(
            HomeFeedCache.feed?.let { HomeUiState.Content(it) } ?: HomeUiState.Loading,
        )
    }
    var homeRefresh by remember { mutableIntStateOf(0) }
    // 「主页」chip 再点一次：选择首页数据源
    var showSitePicker by remember { mutableStateOf(false) }
    // 配置中心类站点自动进入第一个分类的防重标记
    var autoCategoryKey by remember { mutableStateOf<String?>(null) }
    // 纯设置型站点（首页只有分类）：卡片点了直接进详情/动作页，不按标题搜索
    var settingsStyleSite by remember { mutableStateOf(false) }
    // 配置中心动作统一走 AppDialog 弹窗层（注册二维码 / 扫码登录 / Cookie 输入 / 清除）
    var panLogin by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var panCookie by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var panRegister by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var panConfirm by remember {
        mutableStateOf<com.onefilm.android.tv.compose.bridge.PanConfirmState?>(null)
    }
    // 综合设置项（排序 / 弹幕 / DNS / 日志等）也走同一套弹窗
    var panSetting by remember {
        mutableStateOf<Triple<String, String, com.onefilm.android.tv.compose.bridge.PanSetting>?>(null)
    }
    var panForm by remember {
        mutableStateOf<Pair<String, com.onefilm.android.tv.compose.bridge.PanFormSpec>?>(null)
    }
    var panEmby by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    // 网页版配置中心二维码
    var panLink by remember { mutableStateOf<String?>(null) }
    val pageScope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(MobileTab.HOME) }
    // 「继续观看」页默认打开「历史」。
    var libraryTab by rememberSaveable { mutableIntStateOf(1) }
    var categoryTarget by remember { mutableStateOf<HomeCategory?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchHistory by remember { mutableStateOf(MobileSearchHistory.load(context)) }
    var searchActive by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<MobileScreen>(MobileScreen.Root) }
    // 手机端更新检查：进主界面 30 秒后查一次，是否强制由数据源按设置判定。
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress by remember { mutableIntStateOf(-1) }
    var updateError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        delay(30_000L)
        runCatching { ComposeDependencies.updateDataSource.check { info -> updateInfo = info } }
    }

    fun exitSearch() {
        SearchSession.cancel()
        searchActive = false
    }

    fun submitSearch(keywordValue: String = searchQuery) {
        val keyword = keywordValue.trim()
        if (keyword.isEmpty()) return
        searchQuery = keyword
        searchHistory = MobileSearchHistory.add(context, keyword)
        searchActive = true
        categoryTarget = null
        SearchSession.start(ComposeDependencies.searchDataSource, keyword)
    }

    /**
     * 首页 / 分类：首页影片走搜索结果页；
     * 普通站点的分类页影片直接进详情；接口自己的分类页仍走搜索结果。
     */
    fun openSearchFirst(card: MediaCard, fromCategory: Boolean = false) {
        // 配置中心：注册 / 扫码登录 / Cookie 输入 / 清除 全部走统一原生弹窗
        val categoryId = categoryTarget?.id.orEmpty()
        // 动作卡按标题识别，与站点类型无关（配置站的卡片可能出现在任意站点上下文）
        run {
            when {
                card.title.contains("注册") -> {
                    panRegister = Triple(card.key, categoryId, card.title)
                    return
                }
                card.title.contains("扫码") -> {
                    panLogin = Triple(card.key, categoryId, card.title)
                    return
                }
                card.title.contains("清除") -> {
                    val clearer = com.onefilm.android.tv.compose.bridge.PanProviders
                        .clearAction(categoryId, card.title)
                    if (clearer != null) {
                        panConfirm = com.onefilm.android.tv.compose.bridge.PanConfirmState(
                            siteKey = card.key,
                            title = card.title,
                            message = "确定清除该平台已保存的登录信息吗？",
                            action = clearer,
                            body = "此操作只清除本地已保存的登录信息，可随时重新扫码或粘贴。",
                        )
                        return
                    }
                }
                com.onefilm.android.tv.compose.bridge.PanProviders.isManualTitle(card.title) -> {
                    panCookie = Triple(card.key, categoryId, card.title)
                    return
                }
                card.title.contains("Emby", ignoreCase = true) -> {
                    panEmby = Triple(card.key, categoryId, card.title)
                    return
                }
                card.title.contains("刷新登录") || card.title.contains("重建设备") -> {
                    panConfirm = com.onefilm.android.tv.compose.bridge.PanConfirmState(
                        siteKey = card.key,
                        title = card.title,
                        message = "确定执行该操作吗？结果会写回接口配置。",
                        action = if (card.title.contains("重建设备")) "resetPan139Device" else "refreshPan139",
                    )
                    return
                }
            }
            // 表单类：手机号验证码登录 / 账号密码登录 / DIY 配置地址 / WebDAV 备份
            com.onefilm.android.tv.compose.bridge.PanForms.resolve(categoryId, card.title)?.let { spec ->
                panForm = card.key to spec
                return
            }
            if (card.title.contains("网页版配置中心")) {
                panLink = card.webUrl
                    ?: com.onefilm.android.tv.compose.bridge.WebConfigStore.get(card.key)
                return
            }
            // 只有动作卡才继续走「接口页面」兜底；影片卡要落到详情/搜索
            if (com.onefilm.android.tv.compose.bridge.PanProviders.isActionTitle(card.title)) {
                com.onefilm.android.tv.compose.bridge.PanSettings.resolve(card.title)?.let { setting ->
                    panSetting = Triple(card.key, card.title, setting)
                    return
                }
            }
        }
        // 其余动作卡片打开接口下发的网页（备注里带地址，或用基址 + #分类）
        val isAction = com.onefilm.android.tv.compose.bridge.PanProviders.isActionTitle(card.title)
        val target = card.webUrl
            ?: if (settingsStyleSite && isAction) {
                com.onefilm.android.tv.compose.bridge.WebConfigStore.pageUrl(card.key, categoryTarget?.id)
            } else {
                null
            }
        if (!target.isNullOrBlank()) {
            pageScope.launch {
                ComposeDependencies.webPageLauncher?.open(
                    target,
                    if (card.webUrl != null) null else card.title,
                    compact = card.webUrl == null,
                )
            }
            return
        }
        // 普通站点的分类页：影片卡直接进详情播放；接口自己的分类页（配置中心）与首页仍走搜索结果
        if (fromCategory && card.id.isNotBlank()) {
            screen = if (settingsStyleSite) {
                MobileScreen.SearchResults(card.title)
            } else {
                MobileScreen.Detail(card.key, card.id, card.title, card.posterUrl)
            }
            return
        }
        if (card.title.isNotBlank()) screen = MobileScreen.SearchResults(card.title)
    }

    /** 搜索结果 / 收藏 / 历史：已知站点与影片 id，直接进详情。 */
    fun openCardDirect(card: MediaCard) {
        if (card.key.isNotBlank() && card.id.isNotBlank()) {
            screen = MobileScreen.Detail(card.key, card.id, card.title, card.posterUrl)
        } else if (card.title.isNotBlank()) {
            // 兜底：没有 id 的条目（如榜单）按标题去搜，避免"点了没反应"。
            screen = MobileScreen.SearchResults(card.title)
        }
    }

    fun openLive() {
        val activity = context as? Activity ?: return
        // Start immediately so the click is never blocked by a slow/unreachable M3U
        // config; LiveActivity loads the saved live URL itself.
        LiveActivity.start(activity)
    }

    LaunchedEffect(homeRefresh) {
        homeState = runCatching { ComposeDependencies.homeDataSource.load() }
            .fold(
                onSuccess = { feed ->
                    // 配置中心这类站点只返回分类、不返回影片，不能判成空首页
                    if (feed.hero.isEmpty() && feed.sections.isEmpty() && feed.categories.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Content(feed)
                    }
                },
                onFailure = { HomeUiState.Error() },
            )
        // 首页没有影片但有分类：直接进第一个分类（对齐欧歌的「配置中心」体验）
        val feed = (homeState as? HomeUiState.Content)?.feed
        settingsStyleSite = feed?.settingsStyleSite ?: false
        if (feed != null && feed.sections.none { it.items.isNotEmpty() }) {
            val first = feed.categories.firstOrNull()
            val key = first?.let { it.key + ":" + it.id }
            if (first != null && key != autoCategoryKey) {
                autoCategoryKey = key
                categoryTarget = first
            }
        }
    }

    if (showSitePicker) {
        HomeSitePickerDialog(
            options = ComposeDependencies.homeDataSource.sites(),
            onDismiss = { showSitePicker = false },
            onPick = { key ->
                showSitePicker = false
                // 切换成功才刷新；缓存失效由 dataSource.setHomeSite 负责
                if (ComposeDependencies.homeDataSource.setHomeSite(key)) homeRefresh++
            },
        )
    }

    panRegister?.let { (siteKey, categoryId, panTitle) ->
        com.onefilm.android.tv.compose.design.component.PanRegisterDialog(
            siteKey = siteKey,
            categoryId = categoryId,
            title = panTitle,
        ) { panRegister = null }
    }
    panLogin?.let { (siteKey, categoryId, panTitle) ->
        com.onefilm.android.tv.compose.design.component.PanLoginDialog(
            siteKey = siteKey,
            categoryId = categoryId,
            title = panTitle,
        ) {
            panLogin = null
            homeRefresh++
        }
    }
    panCookie?.let { (siteKey, categoryId, panTitle) ->
        com.onefilm.android.tv.compose.design.component.PanCookieDialog(
            siteKey = siteKey,
            categoryId = categoryId,
            title = panTitle,
            onClose = { panCookie = null },
            onSaved = { homeRefresh++ },
        )
    }
    panConfirm?.let { state ->
        com.onefilm.android.tv.compose.design.component.PanConfirmDialog(state) {
            panConfirm = null
            homeRefresh++
        }
    }
    panSetting?.let { (siteKey, panTitle, setting) ->
        val closeAndRefresh: () -> Unit = {
            panSetting = null
            homeRefresh++
        }
        when (setting) {
            is com.onefilm.android.tv.compose.bridge.PanSetting.Choice ->
                com.onefilm.android.tv.compose.design.component.PanChoiceDialog(
                    siteKey = siteKey,
                    setting = setting,
                    onDone = closeAndRefresh,
                )
            com.onefilm.android.tv.compose.bridge.PanSetting.Order ->
                com.onefilm.android.tv.compose.design.component.PanOrderDialog(
                    siteKey = siteKey,
                    title = panTitle,
                    onDone = closeAndRefresh,
                )
            com.onefilm.android.tv.compose.bridge.PanSetting.Logs ->
                com.onefilm.android.tv.compose.design.component.PanLogsDialog(
                    siteKey = siteKey,
                    title = panTitle,
                    onDone = closeAndRefresh,
                )
            com.onefilm.android.tv.compose.bridge.PanSetting.GoStatus ->
                com.onefilm.android.tv.compose.design.component.PanGoStatusDialog(
                    siteKey = siteKey,
                    title = panTitle,
                    onDone = closeAndRefresh,
                )
        }
    }
    panForm?.let { (siteKey, spec) ->
        com.onefilm.android.tv.compose.design.component.PanFormDialog(
            siteKey = siteKey,
            spec = spec,
            onDone = {
                panForm = null
                homeRefresh++
            },
        )
    }
    panEmby?.let { (siteKey, _, panTitle) ->
        com.onefilm.android.tv.compose.design.component.PanEmbyDialog(
            siteKey = siteKey,
            title = panTitle,
            onDone = {
                panEmby = null
                homeRefresh++
            },
        )
    }
    panLink?.let { url ->
        com.onefilm.android.tv.compose.design.component.PanQrDialog(
            title = "扫码打开网页版配置中心",
            message = "用手机扫码打开链接。",
            content = url,
        ) { panLink = null }
    }

    LaunchedEffect(Unit) {
        // Live is a direct action, not a retained Compose destination. Avoid restoring
        // the old portrait placeholder after a process/config recreation.
        if (tab == MobileTab.LIVE) tab = MobileTab.HOME
    }

    BackHandler(enabled = categoryTarget != null) { categoryTarget = null }
    BackHandler(enabled = searchActive) { exitSearch() }

    // 更新弹窗：放在子页面 return 之前渲染，详情/播放页同样能弹出。
    updateInfo?.let { info ->
        BackHandler(enabled = true) {
            if (!info.force && updateProgress < 0) {
                // 「以后再说」只跳过当前版本，不再关闭检查更新总开关。
                ComposeDependencies.updateDataSource.ignore()
                updateProgress = -1
                updateError = null
                updateInfo = null
            }
        }
        MobileUpdateDialog(
            version = info.version,
            notes = info.notes,
            progress = updateProgress,
            error = updateError,
            force = info.force,
            onConfirm = {
                updateProgress = 0
                updateError = null
                ComposeDependencies.updateDataSource.download(
                    url = info.url,
                    onProgress = { updateProgress = it },
                    onSuccess = {
                        updateProgress = -1
                        updateInfo = null
                    },
                    onError = { message ->
                        updateProgress = -1
                        updateError = message.ifBlank {
                            context.getString(com.onefilm.android.tv.compose.R.string.app_update_failed)
                        }
                    },
                )
            },
            onDismiss = {
                if (!info.force) {
                    // 「以后再说」只跳过当前版本，不再关闭检查更新总开关。
                    ComposeDependencies.updateDataSource.ignore()
                    ComposeDependencies.updateDataSource.cancelDownload()
                    updateProgress = -1
                    updateError = null
                    updateInfo = null
                }
            },
        )
    }

    when (val current = screen) {
        is MobileScreen.SearchResults -> {
            MobileSearchResultsScreen(
                keyword = current.keyword,
                onBack = { screen = MobileScreen.Root },
                onCardClick = { card ->
                    screen = MobileScreen.Detail(
                        key = card.key,
                        id = card.id,
                        title = card.title,
                        poster = card.posterUrl,
                        returnTo = current,
                    )
                },
            )
            return
        }

        is MobileScreen.Detail -> {
            MobileDetailScreen(
                key = current.key,
                id = current.id,
                title = current.title,
                poster = current.poster,
                onBack = {
                    PlaybackSession.pause()
                    screen = current.returnTo ?: MobileScreen.Root
                    if (current.returnTo == null) homeRefresh++
                },
                onOpenPlayer = { screen = MobileScreen.Player(current) },
            )
            return
        }

        is MobileScreen.Player -> {
            MobilePlayerScreen(onBack = { screen = current.detail })
            return
        }

        is MobileScreen.NetdiskPlayer -> {
            val back: () -> Unit = {
                // 网盘播放是「临时预览」：返回文件列表时直接停掉，避免声音留在后台。
                PlaybackSession.stop()
                screen = current.returnTo
            }
            MobilePlayerScreen(onBack = back)
            // 系统返回键同样停播，避免只退出界面但声音还在。
            BackHandler(enabled = true) { back() }
            return
        }

        MobileScreen.Root -> Unit
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        MobileTopBar()
        // 搜索栏长期固定在品牌行下方，占满整行。
        MobileSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSubmit = { submitSearch() },
        )
        Box(Modifier.weight(1f)) {
            val category = categoryTarget
            when {
                searchActive -> MobileSearchScreen(
                    history = searchHistory,
                    onClearHistory = {
                        MobileSearchHistory.clear(context)
                        searchHistory = emptyList()
                    },
                    onSubmit = { submitSearch(it) },
                    onCardClick = ::openCardDirect,
                )
                category != null -> MobileCategoryScreen(
                    category = category,
                    dataSource = ComposeDependencies.categoryDataSource,
                    onBack = { categoryTarget = null },
                    onCardClick = { openSearchFirst(it, fromCategory = true) },
                )
                else -> when (tab) {
                    MobileTab.HOME -> MobileHomeScreen(
                        state = homeState,
                        onRetry = { homeRefresh++ },
                        onCardClick = ::openSearchFirst,
                        onCategoryClick = { categoryTarget = it },
                        onHomeClick = { showSitePicker = true },
                    )
                    MobileTab.LIBRARY -> MobileLibraryScreen(
                        dataSource = ComposeDependencies.libraryDataSource,
                        initialTab = libraryTab,
                        onTabChange = { libraryTab = it },
                        onCardClick = ::openCardDirect,
                        onPlayNetdisk = { playback, entry ->
                            val name = entry.name.ifBlank {
                                context.getString(com.onefilm.android.tv.compose.R.string.netdisk_video_name)
                            }
                            PlaybackSession.setQueue(
                                key = "_netdisk",
                                id = entry.path,
                                vodTitle = name,
                                vodPoster = null,
                                flag = "netdisk",
                                episodes = listOf(
                                    com.onefilm.android.tv.compose.feature.player.PlaybackEpisode(name, playback.url),
                                ),
                                index = 0,
                            )
                            PlaybackSession.play(context, playback.url, headers = playback.headers)
                            screen = MobileScreen.NetdiskPlayer()
                        },
                    )
                    MobileTab.LIVE -> MobileLiveScreen(onOpen = ::openLive)
                    // 手机设置直接复用 TV 的设置页：同一套分组与设置项，TV 侧更新自动同步。
                    MobileTab.SETTINGS -> SettingsScreen(
                        dataSource = ComposeDependencies.settingsDataSource,
                        onConfigChanged = { homeRefresh++ },
                        panelMode = true,
                        onRequestClose = { tab = MobileTab.HOME },
                    )
                }
            }
        }
        // 导航栏与内容之间加一条细分隔线。
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        )
        MobileNavBar(
            selected = tab,
            onSelect = { selected ->
                exitSearch()
                if (selected == MobileTab.LIVE) {
                    openLive()
                } else {
                    categoryTarget = null
                    tab = selected
                }
            },
        )
    }
}

/** 顶部品牌行：ONEFILM 与「壹影」同一行居中。 */
@Composable
private fun MobileTopBar() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        // 壹影与 ONEFILM 底端对齐。
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = "ONEFILM",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.mobile_app_brand),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 1.dp),
        )
    }
}

/** 品牌行下的搜索输入框：长条、与设置项统一的边框与高度。 */
@Composable
private fun MobileSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.weight(1f).height(34.dp),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            stringResource(R.string.mobile_search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                }
            },
        )
        Button(
            onClick = onSubmit,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(34.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        ) { Text(stringResource(R.string.menu_search), fontSize = 13.sp) }
    }
}

/** 底部导航：无边框，选中项用主题色高亮；图标在左、文字在右。 */
@Composable
private fun MobileNavBar(selected: MobileTab, onSelect: (MobileTab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileTab.entries.forEach { tab ->
            val active = tab == selected
            val tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else Color.Transparent,
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                androidx.compose.material3.Icon(
                    imageVector = tab.icon,
                    contentDescription = stringResource(tab.labelRes),
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(tab.labelRes),
                    color = tint,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun MobileHomeScreen(
    state: HomeUiState,
    onRetry: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit,
    onHomeClick: () -> Unit,
) {
    // 「主页」chip 常驻：没有接口 / 加载失败时也要能点开首页数据源面板
    val loadedCategories = (state as? HomeUiState.Content)?.feed?.categories.orEmpty()
    Column(Modifier.fillMaxSize()) {
        if (state !is HomeUiState.Content) {
            MobileHomeCategoryChips(
                categories = emptyList(),
                chipsHeight = 46.dp,
                onCategoryClick = onCategoryClick,
                onHomeClick = onHomeClick,
            )
        }
        when (state) {
            HomeUiState.Loading -> MobileLoading()
            HomeUiState.Empty -> MobileStatus(stringResource(R.string.mobile_home_empty), onRetry)
            is HomeUiState.Error -> MobileStatus(state.message, onRetry)
            is HomeUiState.Content -> MobileHomeContent(state, onCardClick, onCategoryClick, onHomeClick)
        }
    }
}

@Composable
private fun MobileHomeContent(
    state: HomeUiState.Content,
    onCardClick: (MediaCard) -> Unit,
    onCategoryClick: (HomeCategory) -> Unit,
    onHomeClick: () -> Unit,
) {
    val feed = state.feed
    val recommendationIndex = feed.sections.indexOfFirst {
        it.key == "recommend" && it.items.isNotEmpty()
    }
    val recommendation = feed.sections.getOrNull(recommendationIndex)?.items
        ?: feed.hero.takeIf { it.isNotEmpty() }
    // 首页只保留「推荐」；收藏/历史/继续观看统一收进导航栏的「继续观看」页面。
    val hasRecommend = !recommendation.isNullOrEmpty()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val hPad = 16.dp
        val vPad = 8.dp
        val rowGap = 8.dp
        val sectionGap = 12.dp
        val hasChips = true
        val chipsHeight = if (hasChips) 46.dp else 0.dp
        val horizontal = com.onefilm.android.tv.compose.design.PosterStyleController.horizontal

        if (!hasRecommend) {
            // 配置中心这类站点没有推荐影片：显示引导，避免空白页
            Column(Modifier.fillMaxSize()) {
                MobileHomeCategoryChips(feed.categories, chipsHeight, onCategoryClick, onHomeClick)
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(com.onefilm.android.tv.compose.R.string.home_no_recommend),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else if (horizontal) {
            // 横版海报：一行两列；第一张是占满整行的英雄卡片，其余按 2 列排列。
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = hPad, vertical = vPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (hasChips) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        MobileHomeCategoryChips(feed.categories, chipsHeight, onCategoryClick, onHomeClick)
                    }
                }
                if (hasRecommend) {
                    val items = recommendation!!
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        com.onefilm.android.tv.mobileui.component.MobileLandscapeCard(
                            card = items.first(),
                            hero = true,                            onClick = { onCardClick(items.first()) },
                        )
                    }
                    itemsIndexed(items.drop(1)) { _, card ->
                        com.onefilm.android.tv.mobileui.component.MobileLandscapeCard(
                            card = card,
                            onClick = { onCardClick(card) },
                        )
                    }
                }
            }
        } else {
            // 把行间距、内边距算进固定高度，避免最后一行被裁掉。
            val fixed = chipsHeight + sectionGap * (if (hasChips) 1 else 0) + rowGap * 2 + vPad * 2
            val rowHeight = ((maxHeight - fixed) / 3).coerceAtLeast(96.dp)
            val cardWidth = (rowHeight * 2f / 3f).coerceAtLeast(64.dp)

            Column(
                Modifier.fillMaxSize().padding(horizontal = hPad, vertical = vPad),
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                MobileHomeCategoryChips(feed.categories, chipsHeight, onCategoryClick, onHomeClick)
                if (hasRecommend) {
                    MobileHomeSection(
                        title = null,
                        items = recommendation!!,
                        rows = 3,
                        rowHeight = rowHeight,
                        cardWidth = cardWidth,
                        rowGap = rowGap,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
}

/** 首页分类 chips：首个固定为「主页」（展示接口推荐数据），其后是接口分类。 */
@Composable
private fun MobileHomeCategoryChips(
    categories: List<HomeCategory>,
    chipsHeight: Dp,
    onCategoryClick: (HomeCategory) -> Unit,
    onHomeClick: () -> Unit,
) {
    val homeLabel = stringResource(com.onefilm.android.tv.compose.R.string.category_home)
    LazyRow(
        Modifier.height(chipsHeight - 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            // 「主页」= 当前页；再点一次弹出「选择首页数据源」。
            Text(
                text = homeLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onHomeClick() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        items(categories) { category ->
            Text(
                text = category.name,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                    .clickable { onCategoryClick(category) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * 首页分区：固定行数的横向网格（非分页），左右连续滑动。
 * 每列是一个纵向 strip，避免出现「一屏一页」的跳转感。
 */
@Composable
private fun MobileHomeSection(
    title: String?,
    items: List<MediaCard>,
    rows: Int,
    rowHeight: Dp,
    cardWidth: Dp,
    rowGap: Dp,
    onCardClick: (MediaCard) -> Unit,
    placeholder: String? = null,
) {
    if (items.isEmpty() && placeholder == null) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (items.isEmpty()) {
            MobileHomePlaceholderCard(cardWidth, rowHeight, placeholder.orEmpty())
        } else {
            MobileHorizontalGrid(
                items = items,
                rows = rows,
                rowHeight = rowHeight,
                cardWidth = cardWidth,
                rowGap = rowGap,
                onCardClick = onCardClick,
            )
        }
    }
}

/** 首页空态占位卡：显示 Logo 和提示文案，有真实数据后自动被替换。 */
@Composable
private fun MobileHomePlaceholderCard(width: Dp, height: Dp, label: String) {
    Column(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun MobileSearchScreen(
    history: List<String>,
    onClearHistory: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onSubmit: (String) -> Unit,
) {
    val state by SearchSession.state.collectAsState()

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (history.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(history) { keyword ->
                        Text(
                            text = keyword,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { onSubmit(keyword) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
                TextButton(onClick = onClearHistory) {
                    Text(stringResource(R.string.mobile_search_clear), fontSize = 12.sp)
                }
            }
        }

        when (val current = state) {
            SearchUiState.Idle -> MobileHint(stringResource(R.string.mobile_search_idle))
            SearchUiState.Loading -> MobileLoading()
            is SearchUiState.Empty -> MobileHint(stringResource(R.string.mobile_search_empty, current.keyword))
            is SearchUiState.Error -> MobileHint(current.message)
            is SearchUiState.Content -> {
                Text(
                    stringResource(R.string.mobile_search_result_count, current.items.size) +
                        if (!current.done) " · " + stringResource(R.string.mobile_search_searching) else "",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (com.onefilm.android.tv.compose.design.PosterStyleController.horizontal) {
                    com.onefilm.android.tv.mobileui.component.MobileLandscapeGrid(
                        items = current.items,
                        modifier = Modifier.weight(1f),
                        showSite = true,
                        onCardClick = onCardClick,
                    )
                } else BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    // 4 行起步，屏幕更高时按可用高度增加行数（平板），列的宽度跟随行高。
                    val rowGap = 8.dp
                    val minRowHeight = 120.dp
                    val rows = ((maxHeight + rowGap) / (minRowHeight + rowGap))
                        .toInt()
                        .coerceAtLeast(4)
                    val rowHeight = ((maxHeight - rowGap * (rows - 1)) / rows)
                        .coerceAtLeast(96.dp)
                    val cardWidth = rowHeight * 2f / 3f
                    MobileHorizontalGrid(
                        items = current.items,
                        rows = rows,
                        rowHeight = rowHeight,
                        cardWidth = cardWidth,
                        rowGap = rowGap,
                        colGap = 10.dp,
                        showSite = true,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileLibraryScreen(
    dataSource: LibraryDataSource,
    initialTab: Int,
    onTabChange: (Int) -> Unit,
    onCardClick: (MediaCard) -> Unit,
    onPlayNetdisk: (com.onefilm.android.tv.compose.bridge.NetdiskPlayback, com.onefilm.android.tv.compose.bridge.NetdiskEntry) -> Unit,
) {
    var data by remember { mutableStateOf<LibraryData?>(null) }
    var pendingDelete by remember { mutableStateOf<MediaCard?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { data = runCatching { dataSource.load() }.getOrNull() }
    val items = if (initialTab == 1) data?.history.orEmpty() else data?.favorites.orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                stringResource(R.string.mobile_tab_library),
                stringResource(R.string.mobile_library_history),
                stringResource(R.string.mobile_library_netdisk),
            ).forEachIndexed { index, label ->
                val active = initialTab == index
                Text(
                    text = label,
                    color = if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            else Color.Transparent,
                        )
                        .clickable { onTabChange(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
        if (initialTab == 2) {
            MobileNetdiskScreen(
                dataSource = ComposeDependencies.netdiskDataSource,
                onPlay = onPlayNetdisk,
            )
        } else if (data == null) {
            MobileLoading()
        } else if (items.isEmpty()) {
            MobileHint(stringResource(R.string.mobile_library_empty))
        } else if (com.onefilm.android.tv.compose.design.PosterStyleController.horizontal) {
            // 横版海报：收藏/历史与首页统一为一行两列 16:9，长按删除。
            com.onefilm.android.tv.mobileui.component.MobileLandscapeGrid(
                items = items,
                modifier = Modifier.weight(1f),
                onCardClick = onCardClick,
                onCardLongClick = { pendingDelete = it },
            )
            pendingDelete?.let { card ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { pendingDelete = null },
                    text = { Text(stringResource(R.string.mobile_library_delete_confirm)) },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingDelete = null
                            scope.launch {
                                if (initialTab == 1) dataSource.deleteHistory(card)
                                else dataSource.deleteFavorite(card)
                                data = runCatching { dataSource.load() }.getOrNull()
                            }
                        }) { Text(stringResource(R.string.menu_delete)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(stringResource(com.onefilm.android.tv.compose.R.string.settings_cancel))
                        }
                    },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.mobile_library_swipe_hint),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { "${it.key}|${it.id}" }) { card ->
                    MobileSwipeDeleteRow(
                        card = card,
                        onClick = { onCardClick(card) },
                        onDelete = {
                            if (initialTab == 1) dataSource.deleteHistory(card)
                            else dataSource.deleteFavorite(card)
                            data = runCatching { dataSource.load() }.getOrNull()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileSwipeDeleteRow(
    card: MediaCard,
    onClick: () -> Unit,
    onDelete: suspend () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val threshold = with(density) { 80.dp.toPx() }
    val maxSwipe = with(density) { 150.dp.toPx() }
    var offsetX by remember(card.key, card.id) { mutableFloatStateOf(0f) }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.9f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.menu_delete),
            color = MaterialTheme.colorScheme.onError,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        MobileMediaRow(
            card = card,
            onClick = onClick,
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(card.key, card.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            offsetX = (offsetX + dragAmount).coerceIn(-maxSwipe, maxSwipe)
                        },
                        onDragEnd = {
                            if (abs(offsetX) >= threshold) {
                                scope.launch {
                                    offsetX = if (offsetX > 0f) maxSwipe else -maxSwipe
                                    onDelete()
                                }
                            } else {
                                offsetX = 0f
                            }
                        },
                        onDragCancel = { offsetX = 0f },
                    )
                },
        )
    }
}

@Composable
private fun MobileMediaRow(
    card: MediaCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileRemoteImage(
            rawUrl = card.posterUrl,
            contentDescription = card.title,
            modifier = Modifier
                .width(64.dp)
                .height(88.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = card.title.ifBlank { stringResource(R.string.mobile_unnamed) },
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val siteName = card.siteName
            if (!siteName.isNullOrBlank()) {
                Text(
                    siteName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            val subtitle = card.subtitle
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MobileLiveScreen(onOpen: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.nav_live), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.mobile_live_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpen) { Text(stringResource(R.string.mobile_live_open)) }
    }
}

@Composable
private fun MobileLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MobileHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MobileStatus(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.mobile_retry)) }
    }
}


/** 从首页/分类榜单卡片进入的搜索结果页：选中一个站点后再进详情。 */
@Composable
private fun MobileSearchResultsScreen(
    keyword: String,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    val state by SearchSession.state.collectAsState()
    LaunchedEffect(keyword) {
        SearchSession.start(ComposeDependencies.searchDataSource, keyword)
    }
    BackHandler { onBack() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                fontSize = 34.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp),
            )
            Text(
                stringResource(R.string.menu_search) + " · " + keyword,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        when (val current = state) {
            SearchUiState.Idle, SearchUiState.Loading -> MobileLoading()
            is SearchUiState.Empty -> MobileHint(stringResource(R.string.mobile_search_empty, current.keyword))
            is SearchUiState.Error -> MobileHint(current.message)
            is SearchUiState.Content -> {
                Text(
                    stringResource(R.string.mobile_search_result_count, current.items.size) +
                        if (!current.done) " · " + stringResource(R.string.mobile_search_searching) else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                if (com.onefilm.android.tv.compose.design.PosterStyleController.horizontal) {
                    com.onefilm.android.tv.mobileui.component.MobileLandscapeGrid(
                        items = current.items,
                        showSite = true,
                        onCardClick = onCardClick,
                    )
                } else BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    val rowGap = 8.dp
                    val minRowHeight = 120.dp
                    val rows = ((maxHeight + rowGap) / (minRowHeight + rowGap))
                        .toInt()
                        .coerceAtLeast(4)
                    val rowHeight = ((maxHeight - rowGap * (rows - 1)) / rows)
                        .coerceAtLeast(96.dp)
                    val cardWidth = rowHeight * 2f / 3f
                    MobileHorizontalGrid(
                        items = current.items,
                        rows = rows,
                        rowHeight = rowHeight,
                        cardWidth = cardWidth,
                        rowGap = rowGap,
                        colGap = 10.dp,
                        showSite = true,
                        onCardClick = onCardClick,
                    )
                }
            }
        }
    }
}

