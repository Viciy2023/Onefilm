package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import com.onefilm.android.tv.compose.bridge.CategoryFilterGroup
import com.onefilm.android.tv.compose.bridge.CategoryFilterOption
import com.onefilm.android.tv.compose.bridge.CategoryPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCategoryDataSource : CategoryDataSource {

    override suspend fun load(
        key: String,
        tid: String,
        page: Int,
        selected: Map<String, String>,
    ): CategoryPage = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val extend = HashMap(selected)
        val result = SiteApi.categoryContent(key, tid, page.toString(), true, extend)
        android.util.Log.d("ComposeCategory", "key=$key tid=$tid list=${result.list.size} firstId=${result.list.firstOrNull()?.id} firstName=${result.list.firstOrNull()?.name}")
        val type = result.types.firstOrNull { it.typeId == tid }
        val filterGroups = if (!type?.filters.isNullOrEmpty()) {
            type!!.filters.map(CategoryFilterStore::fromFilter)
        } else {
            CategoryFilterStore.get(tid)
        }
        CategoryPage(
            title = type?.typeName.orEmpty(),
            items = HomeFeedMapper.fromVods(result.list, key),
            filters = filterGroups,
        )
    }
}
