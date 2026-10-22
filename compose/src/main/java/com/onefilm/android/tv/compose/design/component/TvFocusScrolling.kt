package com.onefilm.android.tv.compose.design.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * TV 焦点滚动策略。
 *
 * 1. 只在目标卡片真正超出可视区时才滚动，避免默认策略为了“居中”产生额外位移；
 * 2. 使用固定 tween 而不是弹性回弹，遥控器连按时不会出现来回抖动。
 */
private val TvFocusBringIntoViewSpec = object : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 180)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = -offset
        val trailingEdge = containerSize - (offset + size)
        return when {
            leadingEdge > 0f -> leadingEdge
            trailingEdge < 0f -> trailingEdge
            else -> 0f
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
