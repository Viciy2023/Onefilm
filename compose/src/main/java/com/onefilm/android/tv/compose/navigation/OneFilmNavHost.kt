package com.onefilm.android.tv.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onefilm.android.tv.compose.feature.library.LibraryScreen
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen

@Composable
fun OneFilmNavHost(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
    searchContent: @Composable () -> Unit,
    detailContent: @Composable (key: String, id: String, title: String) -> Unit,
    categoryContent: @Composable (key: String, tid: String, name: String) -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { homeContent() }
        composable(Routes.SEARCH) { searchContent() }
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
        composable(
            route = Routes.CATEGORY,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("tid") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            categoryContent(
                entry.arguments?.getString("key").orEmpty(),
                entry.arguments?.getString("tid").orEmpty(),
                entry.arguments?.getString("name").orEmpty(),
            )
        }
    }
}
