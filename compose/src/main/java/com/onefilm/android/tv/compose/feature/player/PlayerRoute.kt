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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 全屏播放：横屏、沉浸式，复用详情页小窗的播放会话。 */
@Composable
fun PlayerRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val previousOrientation = remember(activity) { activity?.requestedOrientation }
    // 记录进入全屏前系统栏是否可见：TV 宿主是全屏（状态栏隐藏），退出时必须恢复原状态，
    // 否则状态栏会被重新显示，配合 statusBarsPadding() 会在所有页面顶部顶出一条空白。
    val systemBarsVisibleOnEnter = remember(activity) {
        val view = activity?.window?.decorView
        val platformInsets = view?.rootWindowInsets
        if (view == null || platformInsets == null) {
            false
        } else {
            // 必须经 WindowInsetsCompat 包装，直接调平台 WindowInsets.isVisible(int)
            // 只有 API 30+ 才有，Android 9 上会抛 NoSuchMethodError。
            WindowInsetsCompat.toWindowInsetsCompat(platformInsets, view)
                .isVisible(WindowInsetsCompat.Type.systemBars())
        }
    }

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
                // 恢复进入前的系统栏可见性：TV 宿主原本全屏，就不能在这里把状态栏留在屏幕上。
                if (systemBarsVisibleOnEnter) {
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller?.hide(WindowInsetsCompat.Type.systemBars())
                }
                if (window != null) {
                    WindowCompat.setDecorFitsSystemWindows(window, true)
                    // 立即刷新 inset 分发，避免 Compose 继续沿用全屏时的旧 inset。
                    ViewCompat.requestApplyInsets(window.decorView)
                }
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

    val host = remember { TvPlayerChromeHost() }
    DisposableEffect(host) {
        TvPlayerKeyState.fullscreenActive = true
        onDispose {
            host.clear()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    dispatchPlayerKey(
                        event = event,
                        inline = false,
                        actions = PlayerKeyActions(
                            hidden = { !host.controlsVisible },
                            overlayOpen = { host.overlayOpen },
                            seek = { deltaMs -> TvPlayerKeyState.seekBy?.invoke(deltaMs) },
                            reveal = { TvPlayerKeyState.requestControls?.invoke() },
                            revealToMenu = { TvPlayerKeyState.requestControlsToMenu?.invoke() },
                            togglePlay = { PlaybackSession.toggle() },
                            touch = { TvPlayerKeyState.onUserInteraction?.invoke() },
                        ),
                    )
                },
        ) {
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
                host = host,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
