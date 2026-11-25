package com.onefilm.android.tv.compose.bridge

interface FavoriteStore {
    fun isFavorite(key: String, id: String): Boolean
    fun toggle(key: String, id: String, siteName: String, title: String, posterUrl: String?)
}
