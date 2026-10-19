package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.Filter
import com.onefilm.android.tv.compose.bridge.CategoryFilterGroup
import com.onefilm.android.tv.compose.bridge.CategoryFilterOption
import java.util.concurrent.ConcurrentHashMap

/** 旧版首页会把站点分类的筛选条件缓存起来；分类页从这里取。 */
object CategoryFilterStore {

    private val store = ConcurrentHashMap<String, List<CategoryFilterGroup>>()

    fun put(types: List<com.onefilm.android.tv.bean.Class>) {
        types.forEach { type ->
            if (type.typeId != null) store[type.typeId] = type.filters.map(::fromFilter)
        }
    }

    fun get(tid: String): List<CategoryFilterGroup> = store[tid].orEmpty()

    fun fromFilter(filter: Filter): CategoryFilterGroup = CategoryFilterGroup(
        key = filter.key.orEmpty(),
        name = filter.name.orEmpty(),
        options = filter.value.map { CategoryFilterOption(it.n.orEmpty(), it.v.orEmpty()) },
    )
}
