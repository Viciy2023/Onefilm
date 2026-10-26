package com.onefilm.android.tv.compose.feature.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

/**
 * 全屏播放器按键所需的动作集合，由 PlayerControls 注入当前状态。
 */
class PlayerKeyActions(
    /** 控制栏是否隐藏（隐藏时方向键用于播放控制）。 */
    val hidden: () -> Boolean,
    /** 是否打开了选集/更多/投屏等浮层。 */
    val overlayOpen: () -> Boolean,
    val seek: (Long) -> Unit,
    val reveal: () -> Unit,
    val revealToMenu: () -> Unit,
    val togglePlay: () -> Unit,
    val touch: () -> Unit,
)

/**
 * 全屏播放器的按键路由：
 * - 控件隐藏：左右快进/快退、上下唤出控制栏并聚焦菜单、确认键播放/暂停；
 * - 控件可见：一律返回 false，把方向键交给焦点系统做焦点移动。
 *
 * 抽成纯函数便于单独验证（不依赖 Compose 组合）。
 */
fun dispatchPlayerKey(event: KeyEvent, inline: Boolean, actions: PlayerKeyActions): Boolean {
    if (inline || event.type != KeyEventType.KeyDown) return false
    actions.touch()
    if (actions.overlayOpen() || !actions.hidden()) return false
    return when (event.key) {
        Key.DirectionLeft -> {
            actions.seek(-5_000L)
            true
        }
        Key.DirectionRight -> {
            actions.seek(5_000L)
            true
        }
        Key.DirectionUp, Key.DirectionDown -> {
            actions.revealToMenu()
            true
        }
        Key.DirectionCenter, Key.Enter -> {
            actions.togglePlay()
            actions.reveal()
            true
        }
        else -> false
    }
}
