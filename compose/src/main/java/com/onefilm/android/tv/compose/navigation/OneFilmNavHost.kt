package com.onefilm.android.tv.compose.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

@Composable
fun OneFilmNavHost(
    navController: NavHostController,
    homeContent: @Composable () -> Unit,
) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) { homeContent() }
        composable(Routes.SEARCH) { PlaceholderScreen("搜索") }
        composable(Routes.LIBRARY) { PlaceholderScreen("收藏") }
        composable(Routes.SETTINGS) { PlaceholderScreen("设置") }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}
