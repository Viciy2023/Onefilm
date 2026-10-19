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
import androidx.navigation.compose.rememberNavController
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.feature.home.HomeScreen
import com.onefilm.android.tv.compose.feature.home.HomeViewModel
import com.onefilm.android.tv.compose.navigation.AdaptiveNavigation
import com.onefilm.android.tv.compose.navigation.OneFilmNavHost

class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val dataSource = ComposeDependencies.homeDataSource
        val launcher = ComposeDependencies.detailLauncher

        setContent {
            OneFilmTheme {
                val navController = rememberNavController()
                val route = navController.currentBackStackEntry?.destination?.route
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
                    onNavigate = { target ->
                        navController.navigate(target) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                ) {
                    OneFilmNavHost(navController) {
                        HomeScreen(
                            state = homeState,
                            onRetry = homeViewModel::retry,
                            onCardClick = { card -> launcher.open(this, card) },
                        )
                    }
                }
            }
        }
    }
}
