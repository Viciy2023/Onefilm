package com.onefilm.android.tv.compose.design.component

import androidx.compose.runtime.Composable

/**
 * LITE 侧的焦点滚动策略（降级实现）。
 *
 * TV 端使用 `LocalBringIntoViewSpec` 精确控制滚动距离，但那套 API 是
 * Compose foundation 1.10+ 才有的；LITE 为了 API 21 只能用 1.6.8，
 * 因此这里退回 Compose 默认的 bring-into-view 行为。
 *
 * 行为差异：默认策略倾向于把焦点项“滚到可视区中间”，在极低端机型上
 * 连按遥控器可能出现轻微回弹；不影响焦点链路与播放。
 */
@Composable
fun TvFocusScrolling(content: @Composable () -> Unit) {
    content()
}
