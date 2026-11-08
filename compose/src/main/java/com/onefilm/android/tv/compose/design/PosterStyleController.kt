package com.onefilm.android.tv.compose.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** TV 海报样式：0=竖版 2:3，1=横版 16:9。 */
object PosterStyleController {
    var style by mutableIntStateOf(0)
        private set

    val horizontal: Boolean
        get() = style == 1

    fun apply(value: Int) {
        style = if (value == 1) 1 else 0
    }
}
