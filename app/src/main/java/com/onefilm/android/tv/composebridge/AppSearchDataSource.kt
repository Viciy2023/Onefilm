package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.SearchDataSource
import com.onefilm.android.tv.compose.bridge.SearchPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSearchDataSource : SearchDataSource {

    override suspend fun search(keyword: String, page: Int): SearchPage = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val site = VodConfig.get().getHome()
        if (keyword.isBlank() || site.key.isNullOrBlank()) {
            return@withContext SearchPage(emptyList())
        }
        SearchPage(HomeFeedMapper.fromVods(SiteApi.searchContent(site, keyword, true, page.toString()).list, site.key))
    }
}
