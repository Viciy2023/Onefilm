package com.onefilm.android.tv.ui.activity

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.design.ThemeController
import com.onefilm.android.tv.mobileui.MobileApp

/** Mobile-only Compose host: portrait, touch-first, isolated from the TV Compose host. */
class MobileComposeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContent {
            ThemeController.apply(ComposeDependencies.settingsDataSource.load().themeMode)
            val dark = when (ThemeController.mode) {
                0 -> false
                1 -> true
                else -> isSystemInDarkTheme()
            }
            OneFilmTheme(darkTheme = dark) {
                MobileApp()
            }
        }
    }
}
