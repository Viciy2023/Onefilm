package com.onefilm.android.tv.compose.feature.category

import com.onefilm.android.tv.compose.bridge.CategoryDataSource

/**
 * 分类页预加载。
 *
 * 进入某个分类时，提前拉取左右相邻分类的第一页写入 [CategoryCache]，
 * 用户再切到相邻标签时直接命中缓存，不会先看到 Loading。
 */
object CategoryPreloader {

    private val inFlight = mutableSetOf<String>()

    suspend fun prefetch(key: String, tid: String, dataSource: CategoryDataSource) {
        if (CategoryCache.getState(key, tid) is CategoryUiState.Content) return
        val cacheKey = "$key:$tid"
        synchronized(inFlight) {
            if (!inFlight.add(cacheKey)) return
        }
        try {
            val page = dataSource.load(key, tid, page = 1, selected = emptyMap())
            if (CategoryCache.getState(key, tid) !is CategoryUiState.Content) {
                CategoryCache.putState(
                    key,
                    tid,
                    CategoryUiState.Content(
                        page = page,
                        loadingMore = false,
                        endReached = page.items.isEmpty(),
                    ),
                )
            }
        } catch (_: Throwable) {
            // 预加载失败不提示，正式进入该分类时会重新请求。
        } finally {
            synchronized(inFlight) { inFlight.remove(cacheKey) }
        }
    }
}
