package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import com.onefilm.android.tv.compose.bridge.CategoryPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppCategoryDataSource : CategoryDataSource {

    override suspend fun load(key: String, tid: String, page: Int): CategoryPage =
        withContext(Dispatchers.IO) {
            VodConfig.get().ensureLoaded()
            val result = SiteApi.categoryContent(key, tid, page.toString(), false, HashMap())
            CategoryPage(
                title = result.types.firstOrNull { it.typeId == tid }?.typeName.orEmpty(),
                items = HomeFeedMapper.fromVods(result.list, key),
            )
        }
}
