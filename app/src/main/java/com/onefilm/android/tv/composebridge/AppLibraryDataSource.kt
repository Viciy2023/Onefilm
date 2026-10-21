package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.LibraryData
import com.onefilm.android.tv.compose.bridge.LibraryDataSource
import com.onefilm.android.tv.compose.bridge.MediaCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppLibraryDataSource : LibraryDataSource {
    override suspend fun load(): LibraryData = withContext(Dispatchers.IO) {
        LibraryData(
            favorites = HomeFeedMapper.fromKeeps(Keep.getVod(), Int.MAX_VALUE),
            history = HomeFeedMapper.fromHistory(History.get(), Int.MAX_VALUE),
        )
    }

    override suspend fun deleteFavorite(card: MediaCard) = withContext(Dispatchers.IO) {
        Keep.getVod().firstOrNull {
            it.siteKey == card.key && it.vodId == card.id
        }?.delete()
        Unit
    }

    override suspend fun clearFavorites() = withContext(Dispatchers.IO) {
        Keep.deleteAll()
    }

    override suspend fun deleteHistory(card: MediaCard) = withContext(Dispatchers.IO) {
        History.get().firstOrNull {
            it.siteKey == card.key && it.vodId == card.id
        }?.delete()
        Unit
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        History.clear(VodConfig.getCid())
    }
}
