package com.onefilm.android.tv.mobileui

import android.content.Context

/** Small mobile-only search history store; TV search state is untouched. */
object MobileSearchHistory {
    private const val PREFS = "onefilm_mobile_search"
    private const val KEY = "history"
    private const val LIMIT = 12

    fun load(context: Context): List<String> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "")
            .orEmpty()
        return raw.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(LIMIT)
    }

    fun add(context: Context, keyword: String): List<String> {
        val value = keyword.trim()
        if (value.isEmpty()) return load(context)
        val next = (listOf(value) + load(context).filterNot { it == value }).take(LIMIT)
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, next.joinToString("\n"))
            .apply()
        return next
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY)
            .apply()
    }
}
