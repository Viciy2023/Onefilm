package com.onefilm.android.tv.core

import android.content.Context

object CoreContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun requireContext(): Context =
        appContext ?: error("CoreContext is not initialized")
}
