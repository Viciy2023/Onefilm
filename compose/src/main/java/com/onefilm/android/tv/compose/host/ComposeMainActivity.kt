package com.onefilm.android.tv.compose.host

import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.onefilm.android.tv.compose.navigation.TvHeader
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

                LaunchedEffect(Unit) { homeViewModel.retry() }

                BackHandler(enabled = navController.previousBackStackEntry != null) {
                    navController.popBackStack()
                }
                AdaptiveNavigation(
                    isTv = isTv,
                    currentRoute = route,
                    showTabs = route?.startsWith("detail") != true && route?.startsWith("sources") != true &&
                        route?.startsWith("resolve") != true && route?.startsWith("player") != true,
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
                        TvHeader(
                            currentRoute = route,
                            categories = feed?.categories.orEmpty(),
                            notice = null,
                            siteName = feed?.siteName,
                            onNavigate = { target ->
                                navController.navigate(target) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onLive = { ComposeDependencies.liveLauncher.open(this@ComposeMainActivity) },
                            onConfig = { navController.navigate(Routes.SETTINGS) },
                            onLine = { navController.navigate(Routes.SETTINGS) },
                            onNetwork = { navController.navigate(Routes.SETTINGS) },
                            onCategory = { category ->
                                navController.navigate(Routes.category(category.key, category.id, category.name))
                            },
                        )
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
                                    if (card.key.isNotBlank() && card.id.isNotBlank() &&
                                        !card.id.startsWith("msearch:")
                                    ) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title, card.posterUrl))
                                    } else if (card.title.isNotBlank()) {
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
                        libraryContent = {
                            LibraryRoute(libraryDataSource) { card ->
                                if (card.key.isNotBlank() && card.id.isNotBlank()) navController.navigate(Routes.detail(card.key, card.id, card.title, card.posterUrl))
                            }
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
                                    if (card.key.isNotBlank() && card.id.isNotBlank() &&
                                        !card.id.startsWith("msearch:")
                                    ) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title, card.posterUrl))
                                    } else if (card.title.isNotBlank()) {
                                        navController.navigate(Routes.search(card.title))
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
