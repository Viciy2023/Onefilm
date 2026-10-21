package com.onefilm.android.tv.compose.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
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

/** 全屏播放：横屏、沉浸式，复用详情页小窗的播放会话。 */
@Composable
fun PlayerRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) { activity?.requestedOrientation }

    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                activity?.setPictureInPictureParams(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .setAutoEnterEnabled(true)
                        .build(),
                )
            }
        }
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
                // 恢复进入全屏前由宿主设置的方向，TV 必须回到横屏详情，手机才回到竖屏。
                activity?.requestedOrientation = previousOrientation
                    ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    activity?.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                    )
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize())
        PlayerControls(
            inline = false,
            onToggleFullscreen = onBack,
            onBack = onBack,
            onSelectEpisode = { index ->
                PlaybackSession.playEpisode(
                    context,
                    index,
                    com.onefilm.android.tv.compose.bridge.ComposeDependencies.playbackResolver,
                )
            },
            onPrevious = {
                PlaybackSession.playPrevious(
                    context,
                    com.onefilm.android.tv.compose.bridge.ComposeDependencies.playbackResolver,
                )
            },
            onNext = {
                PlaybackSession.playNext(
                    context,
                    com.onefilm.android.tv.compose.bridge.ComposeDependencies.playbackResolver,
                )
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
