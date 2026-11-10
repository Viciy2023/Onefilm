package com.onefilm.android.tv.ui.activity

import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.design.OneFilmTheme
import com.onefilm.android.tv.compose.design.PosterStyleController
import com.onefilm.android.tv.compose.design.ThemeController
import com.onefilm.android.tv.compose.feature.player.PlaybackSession
import com.onefilm.android.tv.mobileui.MobileApp
import com.onefilm.android.tv.mobileui.player.MobilePlayerState

/** Mobile-only Compose host: portrait, touch-first, isolated from the TV Compose host. */
class MobileComposeActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.github.catvod.utils.LocaleManager.wrap(newBase))
    }

    /**
     * 后台播放设置：0=关（暂停），1=后台继续播放，2=画中画。
     * 与 TV 宿主 [com.onefilm.android.tv.compose.host.ComposeMainActivity] 使用同一套判断。
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        var mode = 2
        try {
            mode = ComposeDependencies.settingsDataSource.backgroundMode()
        } catch (ignored: Throwable) {
            // 设置读取失败时保持默认（画中画）。
        }
        if (mode == 0) {
            PlaybackSession.pause()
            return
        }
        if (mode == 1) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (isInPictureInPictureMode) return
        if (!MobilePlayerState.active) return
        try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } catch (ignored: Throwable) {
            // 部分设备/系统版本不支持时忽略。
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.onefilm.android.tv.compose.bridge.LanguageBridge.current =
            { com.github.catvod.utils.LocaleManager.get() }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.labelOf =
            { com.github.catvod.utils.LocaleManager.label(it) }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.tags =
            { com.github.catvod.utils.LocaleManager.TAGS.toList() }
        com.onefilm.android.tv.compose.bridge.LanguageBridge.apply = { tag ->
            com.github.catvod.utils.LocaleManager.set(tag)
            com.onefilm.android.tv.compose.bridge.HomeFeedCache.feed = null
            recreate()
        }
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // 播放历史写入依赖这个桥接：TV 宿主在 setContent 里设置，手机宿主此前漏了，导致历史页一直为空。
        PlaybackSession.historyStore = ComposeDependencies.playbackHistoryStore
        PosterStyleController.apply(ComposeDependencies.settingsDataSource.load().posterStyle)
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
