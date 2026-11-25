package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.compose.bridge.MediaCard

/** 最近一次搜索结果，供来源页按片名直接分组，避免二次全站搜索。 */
object SearchResultCache {
    @Volatile
    var items: List<MediaCard> = emptyList()
}
