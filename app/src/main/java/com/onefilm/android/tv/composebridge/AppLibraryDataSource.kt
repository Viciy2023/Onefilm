package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppLibraryDataSource : LibraryDataSource {
    override suspend fun load(): LibraryData = withContext(Dispatchers.IO) {
        LibraryData(
            favorites = HomeFeedMapper.fromKeeps(Keep.getVod(), Int.MAX_VALUE),
            history = HomeFeedMapper.fromHistory(History.get(), Int.MAX_VALUE),
        )
    }
}
