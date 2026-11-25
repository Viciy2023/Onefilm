package com.onefilm.android.tv.compose.feature.detail

/**
 * 跨站点自动换源的重试计数。
 *
 * 同一个片名最多自动切换 2 个站点，避免在全部源都失效时无限跳转。
 */
object SiteFallback {

    private const val MAX_SITES = 3

    private var activeTitle: String = ""
    private val tried = linkedSetOf<String>()

    @Synchronized
    fun reset(title: String) {
        if (title != activeTitle) {
            activeTitle = title
            tried.clear()
        }
    }

    @Synchronized
    fun hasBudget(): Boolean = tried.size < MAX_SITES

    @Synchronized
    fun mark(key: String): Boolean = tried.add(key)
}
