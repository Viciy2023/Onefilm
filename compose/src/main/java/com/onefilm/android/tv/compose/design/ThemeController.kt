package com.onefilm.android.tv.compose.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** 0=简约明亮，1=暗黑，2=跟随系统。 */
object ThemeController {
    var mode by mutableIntStateOf(0)
        private set

    fun apply(value: Int) {
        mode = value.coerceIn(0, 2)
    }
}
