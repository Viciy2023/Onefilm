package com.onefilm.android.tv.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onefilm.android.tv.compose.feature.library.LibraryScreen
import com.onefilm.android.tv.compose.feature.search.SearchScreen
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen

@Composable
fun OneFilmNavHost(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
    detailContent: @Composable (key: String, id: String, title: String) -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { homeContent() }
        composable(Routes.SEARCH) { SearchScreen() }
        composable(Routes.LIBRARY) { LibraryScreen() }
        composable(Routes.SETTINGS) { SettingsScreen() }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { entry ->
            detailContent(
                entry.arguments?.getString("key").orEmpty(),
                entry.arguments?.getString("id").orEmpty(),
                entry.arguments?.getString("title").orEmpty(),
            )
        }
    }
}
