package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppPlaybackResolver : PlaybackResolver {
    override suspend fun resolve(key: String, flag: String, episodeUrl: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                SiteApi.playerContent(key, flag, episodeUrl).realUrl
            }.onFailure {
                android.util.Log.e("PlaybackResolver", "resolve failed", it)
            }.getOrNull()?.takeIf { it.isNotBlank() }
        }
}
