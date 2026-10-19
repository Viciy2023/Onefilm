package com.onefilm.android.tv.compose.host

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
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
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.compose.feature.category.CategoryRoute
import com.onefilm.android.tv.compose.feature.detail.DetailRoute
import com.onefilm.android.tv.compose.feature.library.LibraryRoute
import com.onefilm.android.tv.compose.feature.player.PlayerRoute
import com.onefilm.android.tv.compose.feature.search.SearchRoute
import com.onefilm.android.tv.compose.feature.source.SourceRoute
import com.onefilm.android.tv.compose.feature.home.HomeScreen
import com.onefilm.android.tv.compose.feature.home.HomeViewModel
import com.onefilm.android.tv.compose.navigation.AdaptiveNavigation
import com.onefilm.android.tv.compose.navigation.OneFilmNavHost
import com.onefilm.android.tv.compose.navigation.Routes

class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
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
            OneFilmTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val route = backStackEntry?.destination?.route
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
                    showTabs = route?.startsWith("detail") != true && route?.startsWith("sources") != true,
                    onNavigate = { target ->
                        navController.navigate(target) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) {
                    OneFilmNavHost(
                        navController = navController,
                        homeContent = {
                            HomeScreen(
                                state = homeState,
                                onRetry = homeViewModel::retry,
                                onCardClick = { card ->
                                    if (card.key.isNotBlank() && card.id.isNotBlank()) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title))
                                    }
                                },
                                onCategoryClick = { category ->
                                    navController.navigate(
                                        Routes.category(category.key, category.id, category.name),
                                    )
                                },
                            )
                        },
                        searchContent = {
                            SearchRoute(
                                dataSource = searchDataSource,
                                metaDataSource = searchMetaDataSource,
                                onCardClick = { card ->
                                    navController.navigate(Routes.sources(card.title))
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
                        libraryContent = {
                            LibraryRoute(libraryDataSource) { card ->
                                if (card.key.isNotBlank() && card.id.isNotBlank()) navController.navigate(Routes.detail(card.key, card.id, card.title))
                            }
                        },
                        playerContent = { url -> PlayerRoute(url = url, onBack = { navController.popBackStack() }) },
                        detailContent = { key, id, title ->
                            DetailRoute(
                                key = key,
                                id = id,
                                dataSource = detailDataSource,
                                favoriteStore = favoriteStore,
                                onBack = { navController.popBackStack() },
                                onPlay = { episodeName ->
                                    val card = MediaCard(key, id, title, null, null)
                                    if (episodeName.isNullOrBlank()) {
                                        launcher.open(this, card)
                                    } else {
                                        launcher.openEpisode(this, card, episodeName)
                                    }
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
                                    if (card.key.isNotBlank() && card.id.isNotBlank()) {
                                        navController.navigate(Routes.detail(card.key, card.id, card.title))
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
