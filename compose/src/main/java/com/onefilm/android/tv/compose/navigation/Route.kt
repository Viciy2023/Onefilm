package com.onefilm.android.tv.compose.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val SEARCH_QUERY = "search?q={q}"
    const val LIBRARY = "library"
    const val LIVE = "live"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{key}/{id}/{title}?poster={poster}"
    const val CATEGORY = "category/{key}/{tid}/{name}"
    const val SOURCES = "sources/{title}"
    const val RESOLVE = "resolve/{title}"
    const val PLAYER = "player"

    fun detail(key: String, id: String, title: String, poster: String? = null): String =
        "detail/${Uri.encode(key)}/${Uri.encode(id)}/${Uri.encode(title)}?poster=${Uri.encode(poster.orEmpty())}"

    fun sources(title: String): String = "sources/${Uri.encode(title)}"

    fun resolve(title: String): String = "resolve/${Uri.encode(title)}"

    fun search(query: String): String = "search?q=${Uri.encode(query)}"

    fun category(key: String, tid: String, name: String): String =
        "category/${Uri.encode(key)}/${Uri.encode(tid)}/${Uri.encode(name)}"
}
