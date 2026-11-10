package com.onefilm.android.tv.mobileui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.compose.feature.player.PlayerControls

/**
 * 手机全屏播放器：只负责横屏/沉浸和渲染视频层；
 * 控制条、菜单、面板全部复用 TV 的 PlayerControls（host=null 时只走触摸）。
 */
@Composable
fun MobilePlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (window != null) WindowCompat.setDecorFitsSystemWindows(window, true)
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        MobileVideoSurface(Modifier.fillMaxSize())
        PlayerControls(
            inline = false,
            onToggleFullscreen = onBack,
            onBack = onBack,
            onSelectEpisode = { index ->
                PlaybackSession.playEpisode(context, index, ComposeDependencies.playbackResolver)
            },
            onPrevious = {
                PlaybackSession.playPrevious(context, ComposeDependencies.playbackResolver)
            },
            onNext = {
                PlaybackSession.playNext(context, ComposeDependencies.playbackResolver)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
