package com.onefilm.android.tv.compose.design.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.abs

/**
 * TV 焦点滚动策略。
 *
 * 1. 只在目标卡片真正超出可视区时才滚动，避免默认策略为了“居中”产生额外位移；
 * 2. 使用固定 tween 而不是弹性回弹，遥控器连按时不会出现来回抖动。
 */
private val TvFocusBringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 180)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = offset
        val trailingEdge = offset + size
        return when {
            // 已经完全可见，不滚动。
            leadingEdge >= 0f && trailingEdge <= containerSize -> 0f
            // 卡片比可视区还大，保持当前对齐，避免来回推整页。
            leadingEdge < 0f && trailingEdge > containerSize -> 0f
            // 选择距离最近的一条边，产生最小滚动距离。
            abs(leadingEdge) < abs(trailingEdge - containerSize) -> leadingEdge
            else -> trailingEdge - containerSize
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBringIntoViewSpec provides TvFocusBringIntoViewSpec,
        content = content,
    )
}
