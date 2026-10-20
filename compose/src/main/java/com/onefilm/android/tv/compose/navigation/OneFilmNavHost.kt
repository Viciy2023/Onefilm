package com.onefilm.android.tv.compose.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.feature.config.ConfigScreen
import com.onefilm.android.tv.compose.feature.library.LibraryScreen
import com.onefilm.android.tv.compose.feature.lines.LinesScreen
import com.onefilm.android.tv.compose.feature.settings.SettingsScreen

@Composable
fun OneFilmNavHost(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
    searchContent: @Composable (query: String?) -> Unit,
    libraryContent: @Composable (initialTab: Int) -> Unit,
    playerContent: @Composable () -> Unit,
    sourceContent: @Composable (title: String) -> Unit,
    resolveContent: @Composable (title: String) -> Unit,
    detailContent: @Composable (key: String, id: String, title: String, poster: String?) -> Unit,
    categoryContent: @Composable (key: String, tid: String, name: String) -> Unit,
    onConfigChanged: () -> Unit = {},
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { homeContent() }
        composable(Routes.SEARCH) { searchContent(null) }
        composable(
            route = Routes.SEARCH_QUERY,
            arguments = listOf(navArgument("q") { type = NavType.StringType }),
        ) { entry ->
            searchContent(entry.arguments?.getString("q").orEmpty())
        }
        composable(Routes.LIBRARY) { libraryContent(0) }
        composable(Routes.HISTORY) { libraryContent(1) }
        composable(Routes.CONFIG) {
            ConfigScreen(
                dataSource = ComposeDependencies.settingsDataSource,
                onBack = { navController.popBackStack() },
                onDone = {
                    onConfigChanged()
                    if (!navController.popBackStack(Routes.HOME, false)) {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(Routes.LINES) {
            LinesScreen(
                dataSource = ComposeDependencies.settingsDataSource,
                onConfigChanged = onConfigChanged,
                onDone = {
                    if (!navController.popBackStack(Routes.HOME, false)) {
                        navController.navigate(Routes.HOME) { launchSingleTop = true }
                    }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                dataSource = ComposeDependencies.settingsDataSource,
                onConfigChanged = onConfigChanged,
            )
        }
        composable(Routes.PLAYER) { playerContent() }
        composable(
            route = Routes.SOURCES,
            arguments = listOf(navArgument("title") { type = NavType.StringType }),
        ) { entry ->
            sourceContent(entry.arguments?.getString("title").orEmpty())
        }
        composable(
            route = Routes.RESOLVE,
            arguments = listOf(navArgument("title") { type = NavType.StringType }),
        ) { entry ->
            resolveContent(entry.arguments?.getString("title").orEmpty())
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("key") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("poster") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            detailContent(
                entry.arguments?.getString("key").orEmpty(),
                entry.arguments?.getString("id").orEmpty(),
                entry.arguments?.getString("title").orEmpty(),
                entry.arguments?.getString("poster"),
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
