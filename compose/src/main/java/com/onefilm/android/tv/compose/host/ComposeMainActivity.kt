package com.onefilm.android.tv.compose.host

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.navigation.AdaptiveNavigation
import com.onefilm.android.tv.compose.navigation.OneFilmNavHost

class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
            Configuration.UI_MODE_TYPE_TELEVISION

        setContent {
            OneFilmTheme {
                val navController = rememberNavController()
                val route = navController.currentBackStackEntry?.destination?.route
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
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("首页", style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}
