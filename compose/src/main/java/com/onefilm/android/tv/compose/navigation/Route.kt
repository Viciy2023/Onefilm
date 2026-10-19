package com.onefilm.android.tv.compose.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{key}/{id}/{title}"
    const val CATEGORY = "category/{key}/{tid}/{name}"

    fun detail(key: String, id: String, title: String): String =
        "detail/${Uri.encode(key)}/${Uri.encode(id)}/${Uri.encode(title)}"

    fun category(key: String, tid: String, name: String): String =
        "category/${Uri.encode(key)}/${Uri.encode(tid)}/${Uri.encode(name)}"
}
