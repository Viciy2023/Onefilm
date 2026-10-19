package com.onefilm.android.tv.compose.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{key}/{id}/{title}"

    fun detail(key: String, id: String, title: String): String =
        "detail/${Uri.encode(key)}/${Uri.encode(id)}/${Uri.encode(title)}"
}
