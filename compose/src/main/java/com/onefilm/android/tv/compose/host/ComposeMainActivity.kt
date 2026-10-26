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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
import com.onefilm.android.tv.compose.feature.category.CategoryFilterController
import com.onefilm.android.tv.compose.feature.config.ConfigScreen
import com.onefilm.android.tv.compose.feature.lines.LinesScreen
import com.onefilm.android.tv.compose.navigation.OneFilmNavHost
import com.onefilm.android.tv.compose.navigation.Routes
import kotlinx.coroutines.delay

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

                BackHandler(enabled = true) {
                    when {
                        // TV 顶部“主页”按钮只有退出语义：焦点在它上面时按返回
                        // 直接弹退出确认，不再执行回到上一页/分类页的逻辑。
                        isTv && TvHeaderFocusState.homeCategoryFocused -> showExitDialog = true
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
                        }
                        navController.previousBackStackEntry != null -> navController.popBackStack()
                        else -> navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                if (showExitDialog) {
                    BackHandler(enabled = true) { showExitDialog = false }
                    ExitAppDialog(
                        onConfirm = { finish() },
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
                                    updateError = message.ifBlank { "下载失败，请稍后重试。" }
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
                        val variant = when {
                            route?.startsWith(Routes.HOME) == true -> TvHeaderVariant.Full
                            route?.startsWith("category") == true -> TvHeaderVariant.CategoryOnly
                            else -> null
                        }
                        if (variant != null) {
                            val feed = (homeState as? HomeUiState.Content)?.feed
                            TvHeader(
                                currentRoute = route,
                                categories = feed?.categories.orEmpty(),
                                notice = feed?.notice,
                                siteName = feed?.siteName,
                                variant = variant,
                                selectedCategoryKey = selectedCategoryKey,
                                selectedCategoryId = selectedCategoryId,
                                onNavigate = { target ->
                                    navController.navigate(target) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onLive = { ComposeDependencies.liveLauncher.open(this@ComposeMainActivity) },
                                onConfig = { showConfigDialog = true },
                                onLine = { showLinesDialog = true },
                                onNetwork = { navController.navigate(Routes.NETDISK) },
                                onCategory = { category ->
                                    if (route?.startsWith("category") == true &&
                                        CategoryFilterController.matches(category.key, category.id)
                                    ) {
                                        CategoryFilterController.requestShow()
                                    } else {
                                        navController.navigate(Routes.category(category.key, category.id, category.name)) {
                                            // Replace the previous category entry so Back
                                            // always lands on the API home, not category N-1.
                                            popUpTo(Routes.HOME) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                            )
                        }
                        if (showConfigDialog) {
                            ConfigScreen(
                                dataSource = ComposeDependencies.settingsDataSource,
                                onBack = { showConfigDialog = false },
                                onDone = {
                                    showConfigDialog = false
                                    homeViewModel.retry()
                                },
                                onConfigChanged = homeViewModel::retry,
                            )
                        }
                        if (showLinesDialog) {
                            LinesScreen(
                                dataSource = ComposeDependencies.settingsDataSource,
                                onConfigChanged = homeViewModel::retry,
                                onDone = { showLinesDialog = false },
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
                                val name = entry.name.ifBlank { "网盘视频" }
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
                        onConfigChanged = homeViewModel::retry,
                    )
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
