package com.onefilm.android.tv.compose.host

import android.app.PictureInPictureParams
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Rational
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.onefilm.android.tv.compose.navigation.TvHeaderFocusState
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.design.component.TvFocusScrolling
import com.onefilm.android.tv.compose.design.ThemeController
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.feature.category.CategoryRoute
import com.onefilm.android.tv.compose.feature.detail.DetailRoute
import com.onefilm.android.tv.compose.feature.library.LibraryRoute
import com.onefilm.android.tv.compose.feature.player.PlaybackEpisode
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.compose.feature.player.PlayerRoute
import com.onefilm.android.tv.compose.feature.player.TvPlayerKeyState
import com.onefilm.android.tv.compose.feature.player.TvAdFilterTip
import com.onefilm.android.tv.compose.feature.search.SearchRoute
import com.onefilm.android.tv.compose.feature.search.SearchSession
import com.onefilm.android.tv.compose.feature.source.SourceRoute
import com.onefilm.android.tv.compose.feature.source.ResolveRoute
import com.onefilm.android.tv.compose.feature.home.HomeScreen
import com.onefilm.android.tv.compose.feature.home.HomeUiState
import com.onefilm.android.tv.compose.feature.home.HomeViewModel
import com.onefilm.android.tv.compose.navigation.AdaptiveNavigation
import com.onefilm.android.tv.compose.navigation.TvBrandState
import com.onefilm.android.tv.compose.navigation.TvHeader
import com.onefilm.android.tv.compose.navigation.TvHeaderVariant
import com.onefilm.android.tv.compose.navigation.TvSidebar
import com.onefilm.android.tv.compose.navigation.SidebarEntry
import com.onefilm.android.tv.compose.navigation.TvCategoryBar
import com.onefilm.android.tv.compose.navigation.TvBrandRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.focus.FocusRequester
import com.onefilm.android.tv.compose.feature.category.CategoryCache
import com.onefilm.android.tv.compose.feature.category.CategoryFilterController
import com.onefilm.android.tv.compose.feature.category.CategoryPreloader
import com.onefilm.android.tv.compose.feature.config.ConfigScreen
import com.onefilm.android.tv.compose.feature.lines.LinesScreen
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen
import com.onefilm.android.tv.compose.navigation.OneFilmNavHost
import com.onefilm.android.tv.compose.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

class ComposeMainActivity : ComponentActivity() {

    @Volatile
    private var playerRouteActive = false
    private val pendingSearchQuery = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEARCH -> {
                intent.getStringExtra(SearchManager.QUERY)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { pendingSearchQuery.value = it }
            }
            Intent.ACTION_SEND, Intent.ACTION_VIEW -> {
                ComposeDependencies.externalIntentHandler.handle(this, intent)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 控件隐藏时焦点不在任何播放按钮上，Compose 的 onPreviewKeyEvent
        // 收不到按键；这里在全屏播放路由直接处理确认/左右键。
        if (event.action == KeyEvent.ACTION_DOWN && TvPlayerKeyState.fullscreenActive) {
            // 任何按键都算用户操作，用于重置控制栏的 10 秒自动隐藏计时。
            TvPlayerKeyState.onUserInteraction?.invoke()
            if (event.keyCode == KeyEvent.KEYCODE_BACK && TvPlayerKeyState.controlsVisible) {
                // 返回键分层：先关弹窗，再收控制栏，最后才退出全屏。
                if (TvPlayerKeyState.closeOverlay?.invoke() == true) return true
                TvPlayerKeyState.hideControls?.invoke()
                return true
            }
            if (!TvPlayerKeyState.controlsVisible) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        // 控制栏隐藏时左右键只负责快进/快退，不唤出控制栏；
                        // 位置反馈由播放器的短提示给出。
                        TvPlayerKeyState.seekBy?.invoke(-5_000L)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        TvPlayerKeyState.seekBy?.invoke(5_000L)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        // 控件隐藏时上下键唤出控制栏，焦点直接落到菜单行第一项。
                        TvPlayerKeyState.requestControlsToMenu?.invoke()
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        PlaybackSession.toggle()
                        TvPlayerKeyState.requestControls?.invoke()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPipIfPossible()
    }

    private fun enterPipIfPossible() {
        if (!playerRouteActive || isInPictureInPictureMode) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build(),
            )
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.github.catvod.utils.LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.onefilm.android.tv.compose.bridge.LanguageBridge.current =
            { com.github.catvod.utils.LocaleManager.get() }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf =
            { com.github.catvod.utils.LocaleManager.label(it) }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.tags =
            { com.github.catvod.utils.LocaleManager.TAGS.toList() }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.apply = { tag ->
            com.github.catvod.utils.LocaleManager.set(tag)
            com.onefilm.android.tv.compose.bridge.HomeFeedCache.feed = null
            recreate()
        }
        handleExternalIntent(intent)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION ||
            !packageName.endsWith(".mobile")
        if (isTv) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val dataSource = ComposeDependencies.homeDataSource
        val detailDataSource = ComposeDependencies.detailDataSource
        val categoryDataSource = ComposeDependencies.categoryDataSource
        val searchDataSource = ComposeDependencies.searchDataSource
        val searchMetaDataSource = ComposeDependencies.searchMetaDataSource
        val libraryDataSource = ComposeDependencies.libraryDataSource
        val favoriteStore = ComposeDependencies.favoriteStore
        val sourceDataSource = ComposeDependencies.sourceDataSource

        setContent {
            PlaybackSession.historyStore = ComposeDependencies.playbackHistoryStore
            ThemeController.apply(ComposeDependencies.settingsDataSource.load().themeMode)
            OneFilmTheme(
                darkTheme = when (ThemeController.mode) {
                    0 -> false
                    1 -> true
                    else -> androidx.compose.foundation.isSystemInDarkTheme()
                },
            ) {
                val navController = rememberNavController()
                val pendingSearch = pendingSearchQuery.value
                LaunchedEffect(pendingSearch) {
                    if (!pendingSearch.isNullOrBlank()) {
                        navController.navigate(Routes.search(pendingSearch)) { launchSingleTop = true }
                        pendingSearchQuery.value = null
                    }
                }
                var searchFocusToken by remember { mutableIntStateOf(0) }
                var homeCategoryFocusToken by remember { mutableIntStateOf(0) }
                var showSettingsPanel by remember { mutableStateOf(false) }
                var searchResultVisible by remember { mutableStateOf(false) }
                val prefetchScope = rememberCoroutineScope()
                val sidebarFirstFocus = remember { FocusRequester() }
                val emptyActionFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(300)
                    runCatching { sidebarFirstFocus.requestFocus() }
                }
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val categoryArgs = backStackEntry?.arguments
                val selectedCategoryKey = categoryArgs?.getString("key")
                val selectedCategoryId = categoryArgs?.getString("tid")
                LaunchedEffect(route) {
                    TvHeaderFocusState.homeRoute = route?.startsWith(Routes.HOME) == true
                    if (TvHeaderFocusState.homeRoute) TvHeaderFocusState.navFocusedOnce = false
                    // 顶部导航只存在于首页/分类页；离开这些页面时清掉“主页按钮”
                    // 焦点标记，避免详情/播放页按返回误弹退出。
                    if (route?.startsWith(Routes.HOME) != true &&
                        route?.startsWith("category") != true
                    ) {
                        TvHeaderFocusState.homeCategoryFocused = false
                    }
                    if (route?.startsWith("search") == true) searchFocusToken++
                    playerRouteActive = route?.startsWith("player") == true
                    val keepPlaying = route?.startsWith("detail") == true ||
                        route?.startsWith("player") == true
                    if (!keepPlaying) PlaybackSession.release()
                    val keepSearching = route?.startsWith("search") == true ||
                        route?.startsWith("detail") == true ||
                        route?.startsWith("sources") == true ||
                        route?.startsWith("resolve") == true ||
                        route?.startsWith("player") == true
                    if (!keepSearching) SearchSession.cancel()
                }
                val homeViewModel: HomeViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { HomeViewModel(dataSource) }
                    },
                )
                val homeState by homeViewModel.state.collectAsState()
                val brandFeed = (homeState as? HomeUiState.Content)?.feed
                LaunchedEffect(brandFeed?.notice, brandFeed?.siteName) {
                    TvBrandState.notice = brandFeed?.notice.orEmpty()
                    TvBrandState.siteName = brandFeed?.siteName.orEmpty()
                }
                var showConfigDialog by remember { mutableStateOf(false) }
                var showExitDialog by remember { mutableStateOf(false) }
                var showStorageIntro by remember {
                    mutableStateOf(ComposeDependencies.settingsDataSource.shouldShowStorageIntro())
                }
                var showLinesDialog by remember { mutableStateOf(false) }
                var updateInfo by remember { mutableStateOf<com.onefilm.android.tv.compose.bridge.UpdateInfo?>(null) }
                var updateProgress by remember { mutableIntStateOf(-1) }
                var updateError by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(Unit) {
                    // 启动进入主界面后等待 60 秒再检查更新，避免打扰首屏播放。
                    delay(60_000L)
                    runCatching {
                        ComposeDependencies.updateDataSource.check { info -> updateInfo = info }
                    }
                }

                LaunchedEffect(Unit) { homeViewModel.retry() }

                if (showStorageIntro) {
                    StorageIntroDialog(
                        onGrant = {
                            ComposeDependencies.settingsDataSource.markStorageIntroDone()
                            showStorageIntro = false
                            runCatching {
                                startActivity(
                                    Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        android.net.Uri.parse("package:$packageName"),
                                    ),
                                )
                            }.onFailure {
                                runCatching {
                                    startActivity(
                                        Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                                    )
                                }
                            }
                        },
                        onDismiss = {
                            ComposeDependencies.settingsDataSource.markStorageIntroDone()
                            showStorageIntro = false
                        },
                    )
                }
                if (showExitDialog) {
                    BackHandler(enabled = true) { showExitDialog = false }
                    ExitAppDialog(
                        onConfirm = {
                            // 先停掉播放器和前台播放服务，再关界面，避免退出后还在出声
                            PlaybackSession.release()
                            finish()
                        },
                        onDismiss = { showExitDialog = false },
                    )
                }
                updateInfo?.let { info ->
                    BackHandler(enabled = true) {
                        if (!info.force && updateProgress < 0) {
                            updateInfo = null
                            updateError = null
                        }
                    }
                    UpdatePromptDialog(
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
                                    updateError = message.ifBlank { getString(R.string.app_update_failed) }
                                },
                            )
                        },
                        onDismiss = {
                            if (!info.force) {
                                ComposeDependencies.updateDataSource.cancelDownload()
                                updateProgress = -1
                                updateError = null
                                updateInfo = null
                            }
                        },
                    )
                }
                Box(Modifier.fillMaxSize()) {
                TvFocusScrolling {
                AdaptiveNavigation(
                    isTv = isTv,
                    currentRoute = route,
                    showTabs = route?.startsWith("detail") != true && route?.startsWith("sources") != true &&
                        route?.startsWith("resolve") != true && route?.startsWith("player") != true,
                    transparentBackground = isTv,
                    wallpaper = if (isTv) {
                        { LegacyWallpaper() }
                    } else null,
                    onNavigate = { target ->
                        if (target == Routes.LIVE) {
                            ComposeDependencies.liveLauncher.open(this@ComposeMainActivity)
                        } else {
                            navController.navigate(target) {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    tvHeader = {
                        val feed = (homeState as? HomeUiState.Content)?.feed
                        val showBrand = route?.startsWith("detail") != true &&
                            route?.startsWith("sources") != true &&
                            route?.startsWith("resolve") != true &&
                            route?.startsWith("player") != true
                        if (showBrand) {
                            TvBrandRow(
                                centerText = feed?.notice?.takeIf { it.isNotBlank() }
                                    ?: feed?.siteName?.takeIf { it.isNotBlank() }
                                    ?: "ONEFILM",
                                horizontalPadding = 24.dp,
                            )
                        }
                        if (showConfigDialog) {
                            ConfigScreen(
                                dataSource = ComposeDependencies.settingsDataSource,
                                onBack = { showConfigDialog = false },
                                onDone = {
                                    showConfigDialog = false
                                    CategoryCache.clear()
                                    homeViewModel.retry()
                                },
                                onConfigChanged = { CategoryCache.clear(); homeViewModel.retry() },
                            )
                        }
                        if (showLinesDialog) {
                            LinesScreen(
                                dataSource = ComposeDependencies.settingsDataSource,
                                onConfigChanged = { CategoryCache.clear(); homeViewModel.retry() },
                                onDone = { showLinesDialog = false },
                            )
                        }
                    },
                    sidebar = {
                        val showSidebar = route?.startsWith("detail") != true &&
                            route?.startsWith("sources") != true &&
                            route?.startsWith("resolve") != true &&
                            route?.startsWith("player") != true &&
                            route?.startsWith("search?") != true &&
                            !(route == Routes.SEARCH && searchResultVisible)
                        if (showSidebar) {
                            TvSidebar(
                                entries = listOf(
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_vod),
                                        icon = Icons.Outlined.Home,
                                        selected = route == Routes.HOME ||
                                            route?.startsWith("category") == true,
                                    ) {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_history),
                                        icon = Icons.Outlined.History,
                                        selected = route == Routes.HISTORY,
                                    ) {
                                        navController.navigate(Routes.HISTORY) { launchSingleTop = true }
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_favorites),
                                        icon = Icons.Outlined.Star,
                                        selected = route == Routes.LIBRARY,
                                    ) {
                                        navController.navigate(Routes.LIBRARY) { launchSingleTop = true }
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_search),
                                        icon = Icons.Outlined.Search,
                                        selected = route == Routes.SEARCH,
                                    ) {
                                        navController.navigate(Routes.SEARCH) { launchSingleTop = true }
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_live),
                                        icon = Icons.Outlined.LiveTv,
                                        selected = false,
                                    ) {
                                        ComposeDependencies.liveLauncher.open(this@ComposeMainActivity)
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_config),
                                        icon = Icons.Outlined.Tune,
                                        selected = showConfigDialog,
                                    ) {
                                        showConfigDialog = true
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_lines),
                                        icon = Icons.Outlined.SwapHoriz,
                                        selected = showLinesDialog,
                                    ) {
                                        showLinesDialog = true
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_netdisk),
                                        icon = Icons.Outlined.Cloud,
                                        selected = route == Routes.NETDISK,
                                    ) {
                                        navController.navigate(Routes.NETDISK) { launchSingleTop = true }
                                    },
                                    SidebarEntry(
                                        label = stringResource(R.string.nav_settings),
                                        icon = Icons.Outlined.Settings,
                                        selected = showSettingsPanel,
                                    ) {
                                        showSettingsPanel = true
                                    },
                                ),
                                firstFocusRequester = sidebarFirstFocus,
                                rightFocusRequester = if (homeState is HomeUiState.Empty) emptyActionFocus else null,
                            )
                        }
                    },
                    categoryBar = {
                        val showCategory = route == Routes.HOME || route?.startsWith("category") == true
                        if (showCategory) {
                            val feed = (homeState as? HomeUiState.Content)?.feed
                            TvCategoryBar(
                                currentRoute = route,
                                categories = feed?.categories.orEmpty(),
                                selectedCategoryKey = selectedCategoryKey,
                                selectedCategoryId = selectedCategoryId,
                                onNavigate = { target ->
                                    if (target == Routes.SETTINGS) {
                                        showSettingsPanel = true
                                    } else {
                                        navController.navigate(target) {
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                onCategory = { category ->
                                    if (route?.startsWith("category") == true &&
                                        CategoryFilterController.matches(category.key, category.id)
                                    ) {
                                        CategoryFilterController.requestShow()
                                    } else {
                                        val categories = (homeState as? HomeUiState.Content)?.feed?.categories.orEmpty()
                                        val index = categories.indexOfFirst {
                                            it.key == category.key && it.id == category.id
                                        }
                                        if (index >= 0) {
                                            listOf(index - 1, index + 1).forEach { neighbor ->
                                                categories.getOrNull(neighbor)?.let { item ->
                                                    prefetchScope.launch {
                                                        CategoryPreloader.prefetch(item.key, item.id, categoryDataSource)
                                                    }
                                                }
                                            }
                                        }
                                        navController.navigate(Routes.category(category.key, category.id, category.name)) {
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                hasCategoryFilters = { category ->
                                    val version = CategoryFilterController.filtersVersion
                                    version >= 0 && ComposeDependencies.categoryDataSource.hasFilters(category.id)
                                },
                                onHomeFocused = { home ->
                                    TvHeaderFocusState.homeCategoryFocused = home
                                },
                                leftFocus = sidebarFirstFocus,
                                focusHomeToken = homeCategoryFocusToken,
                            )
                        }
                    },
                ) {
                    OneFilmNavHost(
                        navController = navController,
                        homeContent = {
                            HomeScreen(
                                state = homeState,
                                isTv = isTv,
                                onRetry = homeViewModel::retry,
                                onOpenConfig = { showConfigDialog = true },
                                firstActionFocus = emptyActionFocus,
                                onCardClick = { card ->
                                    if (card.title.isNotBlank()) {
                                        navController.navigate(Routes.search(card.title))
                                    }
                                },
                                onCategoryClick = { category ->
                                    navController.navigate(
                                        Routes.category(category.key, category.id, category.name),
                                    )
                                },
                            )
                        },
                        searchContent = { initialQuery ->
                            SearchRoute(
                                focusToken = searchFocusToken,
                                dataSource = searchDataSource,
                                metaDataSource = searchMetaDataSource,
                                initialQuery = initialQuery,
                                onResultVisibilityChange = { searchResultVisible = it },
                                onCardClick = { card ->
                                    if (card.key.isNotBlank() && card.id.isNotBlank() &&
                                        !card.id.startsWith("msearch:")
                                    ) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title, card.posterUrl))
                                    } else if (card.title.isNotBlank()) {
                                        navController.navigate(Routes.sources(card.title))
                                    }
                                },
                            )
                        },
                        sourceContent = { title ->
                            SourceRoute(
                                title = title,
                                dataSource = sourceDataSource,
                                onBack = { navController.popBackStack() },
                                onSelect = { item ->
                                    if (item.key.isNotBlank() && item.id.isNotBlank()) {
                                        navController.navigate(
                                            Routes.detail(
                                                item.key,
                                                item.id,
                                                item.title,
                                                item.posterUrl,
                                            ),
                                        )
                                    }
                                },
                            )
                        },
                        resolveContent = { title ->
                            ResolveRoute(
                                title = title,
                                dataSource = sourceDataSource,
                                onResolved = { item ->
                                    navController.navigate(
                                        Routes.detail(item.key, item.id, item.title, item.posterUrl),
                                    ) {
                                        popUpTo(Routes.RESOLVE) { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() },
                                onSearch = {
                                    navController.navigate(Routes.SEARCH) {
                                        popUpTo(Routes.RESOLVE) { inclusive = true }
                                    }
                                },
                            )
                        },
                        libraryContent = { initialTab ->
                            LibraryRoute(
                                dataSource = libraryDataSource,
                                initialTab = initialTab,
                                onCardClick = { card ->
                                    if (card.key.isNotBlank() && card.id.isNotBlank()) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title, card.posterUrl))
                                    }
                                },
                            )
                        },
                        playerContent = { PlayerRoute(onBack = { navController.popBackStack() }) },
                        detailContent = { key, id, title, poster ->
                            DetailRoute(
                                key = key,
                                id = id,
                                previewTitle = title,
                                previewPoster = poster,
                                dataSource = detailDataSource,
                                favoriteStore = favoriteStore,
                        playbackResolver = ComposeDependencies.playbackResolver,
                        historyStore = ComposeDependencies.playbackHistoryStore,
                                onBack = { navController.popBackStack() },
                                onEnterFullscreen = { navController.navigate(Routes.PLAYER) },
                                searchDataSource = ComposeDependencies.searchDataSource,
                                onSwitchSite = { newKey, newId, newTitle, newPoster ->
                                    navController.navigate(Routes.detail(newKey, newId, newTitle, newPoster))
                                },
                            )
                        },
                        categoryContent = { key, tid, name ->
                            CategoryRoute(
                                key = key,
                                tid = tid,
                                title = name,
                                dataSource = categoryDataSource,
                                onBack = { navController.popBackStack() },
                                onCardClick = { card ->
                                    if (card.title.isNotBlank()) {
                                        navController.navigate(Routes.search(card.title))
                                    }
                                },
                            )
                        },
                        netdiskContent = {
                            com.onefilm.android.tv.compose.feature.netdisk.NetdiskScreen(
                                dataSource = ComposeDependencies.netdiskDataSource,
                            ) { playback, entry ->
                                val name = entry.name.ifBlank { getString(R.string.netdisk_video_name) }
                                PlaybackSession.setQueue(
                                    key = "_netdisk",
                                    id = entry.path,
                                    vodTitle = name,
                                    vodPoster = null,
                                    flag = "netdisk",
                                    episodes = listOf(PlaybackEpisode(name, playback.url)),
                                    index = 0,
                                )
                                PlaybackSession.play(
                                    this@ComposeMainActivity,
                                    playback.url,
                                    headers = playback.headers,
                                )
                                navController.navigate(Routes.PLAYER)
                            }
                        },
                        onConfigChanged = { CategoryCache.clear(); homeViewModel.retry() },
                    )
                }
                }
                SettingsPanelOverlay(
                    visible = showSettingsPanel,
                    onDismiss = { showSettingsPanel = false },
                    onConfigChanged = { CategoryCache.clear(); homeViewModel.retry() },
                )
                TvAdFilterTip(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 64.dp),
                )
                }
                // 返回键统一由这里处理。必须放在 NavHost 之后组合：navigation-compose 2.7.7
                // 的 NavHost 内部自带一个 BackHandler（NavHost.kt:216，返回栈深度 > 1 时启用），
                // 后注册的优先；放在它前面会被它抢先弹栈，表现就是“回到主页后按返回变成返回上一步”。
                BackHandler(enabled = !showSettingsPanel) {
                    when {
                        // 只有主页按返回才弹退出确认；分类页一律先回主页。
                        route?.startsWith(Routes.HOME) == true -> showExitDialog = true
                        // Any API category returns straight to home, never to the
                        // previously focused category.
                        route?.startsWith("category") == true -> {
                            if (!navController.popBackStack(Routes.HOME, false)) {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.startDestinationId) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                            // 回到主页后把焦点还给“主页”分类标签。
                            homeCategoryFocusToken++
                        }
                        // 侧栏页面返回点播主页，不回到上一个侧栏页面。
                        route == Routes.HISTORY || route == Routes.LIBRARY ||
                            route == Routes.SEARCH || route == Routes.NETDISK -> {
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.startDestinationId) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        navController.previousBackStackEntry != null -> navController.popBackStack()
                        else -> navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegacyWallpaper() {
    val context = LocalContext.current
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            runCatching {
                val type = Class.forName("com.onefilm.android.tv.ui.custom.CustomWallView")
                type.getConstructor(Context::class.java, AttributeSet::class.java)
                    .newInstance(ctx, null) as View
            }.getOrNull() ?: View(ctx)
        },
    )
}

@Composable
private fun SettingsPanelOverlay(
    visible: Boolean,
    onDismiss: () -> Unit,
    onConfigChanged: () -> Unit,
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        var appeared by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { appeared = true }
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = appeared,
                enter = fadeIn(tween(180)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                )
            }
            AnimatedVisibility(
                visible = appeared,
                enter = slideInHorizontally(tween(200)) { it } + fadeIn(tween(200)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.4f)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f)),
                ) {
                    SettingsScreen(
                        dataSource = ComposeDependencies.settingsDataSource,
                        onConfigChanged = onConfigChanged,
                        panelMode = true,
                        onRequestClose = onDismiss,
                    )
                }
            }
        }
    }
}
