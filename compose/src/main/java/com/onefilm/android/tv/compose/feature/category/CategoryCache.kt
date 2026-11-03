package com.onefilm.android.tv.compose.feature.category

/**
 * 分类页内存缓存。
 *
 * 导航到另一个分类时，旧分类的 NavBackStackEntry 会被 popUpTo 销毁，
 * CategoryViewModel 也随之清空；这里按 key:tid 保留上次的页面状态和筛选条件，
 * 重新进入时先渲染缓存，用户就看不到整页 Loading 的闪烁。
 */
object CategoryCache {

    private val states = mutableMapOf<String, CategoryUiState>()

    private fun mapKey(key: String, tid: String) = "$key:$tid"

    @Synchronized
    fun getState(key: String, tid: String): CategoryUiState? = states[mapKey(key, tid)]

    @Synchronized
    fun putState(key: String, tid: String, state: CategoryUiState) {
        states[mapKey(key, tid)] = state
    }

    @Synchronized
    fun clear() {
        states.clear()
    }
}
