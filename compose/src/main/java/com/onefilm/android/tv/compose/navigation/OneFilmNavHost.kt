package com.onefilm.android.tv.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.onefilm.android.tv.compose.feature.library.LibraryScreen
import com.onefilm.android.tv.compose.feature.search.SearchScreen
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen

@Composable
fun OneFilmNavHost(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { homeContent() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.LIBRARY) { LibraryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
    }
}
