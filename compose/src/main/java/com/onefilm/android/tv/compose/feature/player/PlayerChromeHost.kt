package com.onefilm.android.tv.compose.feature.player

/**
 * 播放器共享 UI（PlayerControls）与宿主输入层之间的桥。
 *
 * - TV 宿主使用 [TvPlayerChromeHost]：接管 D-pad、焦点、TvPlayerKeyState；
 * - 手机宿主传 null：共享 UI 不请求焦点，只保留触摸。
 */
interface PlayerChromeHost {
    /** 是否需要焦点管理（TV=true，触摸=false）。 */
    val usesFocus: Boolean get() = false

    fun onControlsVisible(visible: Boolean) {}

    fun onMenuOpen(open: Boolean) {}

    fun onPlayFocused(focused: Boolean) {}

    /** 共享 UI 把“唤出/隐藏控制栏、关闭最上层、seek、用户操作”注册给宿主。 */
    fun bind(
        reveal: () -> Unit,
        revealToMenu: () -> Unit,
        hide: () -> Unit,
        closeTop: () -> Boolean,
        seekBy: (Long) -> Unit,
        userInteraction: () -> Unit,
    ) {
    }
}

/** TV 宿主的实现：所有回调落到 TvPlayerKeyState，Activity 的按键处理继续使用它。 */
class TvPlayerChromeHost : PlayerChromeHost {
    var controlsVisible: Boolean = true
        private set
    var overlayOpen: Boolean = false
        private set

    override val usesFocus: Boolean get() = true

    override fun onControlsVisible(visible: Boolean) {
        controlsVisible = visible
        TvPlayerKeyState.controlsVisible = visible
        if (!visible) TvPlayerKeyState.playFocused = false
    }

    override fun onMenuOpen(open: Boolean) {
        overlayOpen = open
        TvPlayerKeyState.menuOpen = open
    }

    override fun onPlayFocused(focused: Boolean) {
        TvPlayerKeyState.playFocused = focused
    }

    override fun bind(
        reveal: () -> Unit,
        revealToMenu: () -> Unit,
        hide: () -> Unit,
        closeTop: () -> Boolean,
        seekBy: (Long) -> Unit,
        userInteraction: () -> Unit,
    ) {
        TvPlayerKeyState.requestControls = reveal
        TvPlayerKeyState.requestControlsToMenu = revealToMenu
        TvPlayerKeyState.hideControls = hide
        TvPlayerKeyState.closeOverlay = closeTop
        TvPlayerKeyState.seekBy = seekBy
        TvPlayerKeyState.onUserInteraction = userInteraction
    }

    fun clear() {
        TvPlayerKeyState.fullscreenActive = false
        TvPlayerKeyState.requestControls = null
        TvPlayerKeyState.requestControlsToMenu = null
        TvPlayerKeyState.hideControls = null
        TvPlayerKeyState.closeOverlay = null
        TvPlayerKeyState.seekBy = null
        TvPlayerKeyState.onUserInteraction = null
    }
}
