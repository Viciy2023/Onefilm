package com.onefilm.android.tv.compose.bridge

import java.util.concurrent.ConcurrentHashMap

/** 详情内存缓存：搜索结果点开过的影片再次进入直接秒开。 */
object DetailCache {
    private val cache = ConcurrentHashMap<String, DetailInfo>()

    fun get(key: String, id: String): DetailInfo? = cache["$key:$id"]

    fun clear() = cache.clear()

    fun put(info: DetailInfo) {
        cache["${info.key}:${info.id}"] = info
    }
}
