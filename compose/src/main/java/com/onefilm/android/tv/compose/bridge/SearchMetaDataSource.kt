package com.onefilm.android.tv.compose.bridge

interface SearchMetaDataSource {
    fun history(): List<String>
    fun hot(): List<String>
    fun record(keyword: String)
    suspend fun suggest(text: String): List<String>
    suspend fun refreshHot(): List<String>
}
