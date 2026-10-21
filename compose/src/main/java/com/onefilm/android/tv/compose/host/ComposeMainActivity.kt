package com.onefilm.android.tv.compose.host

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.util.Rational
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.design.ThemeController
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.feature.category.CategoryRoute
import com.onefilm.android.tv.compose.feature.detail.DetailRoute
import com.onefilm.android.tv.compose.feature.library.LibraryRoute
import com.onefilm.android.tv.compose.feature.player.PlaybackEpisode
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.compose.feature.player.PlayerRoute
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

class ComposeMainActivity : ComponentActivity() {

    @Volatile
    private var playerRouteActive = false

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
        val launcher = ComposeDependencies.detailLauncher

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
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
                val categoryArgs = backStackEntry?.arguments
                val selectedCategoryKey = categoryArgs?.getString("key")
                val selectedCategoryId = categoryArgs?.getString("tid")
                LaunchedEffect(route) {
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
                var showLinesDialog by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) { homeViewModel.retry() }

                BackHandler(enabled = true) {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else if (route?.startsWith(Routes.HOME) != true) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        finish()
                    }
                }
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
                                        navController.navigate(Routes.category(category.key, category.id, category.name))
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
                                        launcher.open(
                                            this,
                                            MediaCard(item.key, item.id, item.title, item.subtitle, item.posterUrl),
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
