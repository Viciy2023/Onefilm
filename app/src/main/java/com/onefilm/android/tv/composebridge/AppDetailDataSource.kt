package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.DetailEpisode
import com.onefilm.android.tv.compose.bridge.DetailFlag
import com.onefilm.android.tv.compose.bridge.DetailInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDetailDataSource : DetailDataSource {

    override suspend fun load(key: String, id: String): DetailInfo = withContext(Dispatchers.IO) {
        VodConfig.get().ensureLoaded()
        val vod = SiteApi.detailContent(key, id).vod.setFlags()
        DetailInfo(
            key = key,
            id = id,
            title = vod.name.orEmpty().ifBlank { "未命名" },
            posterUrl = vod.pic.orEmpty().ifBlank { null },
            description = vod.content.orEmpty().ifBlank { null },
            year = vod.year.orEmpty().ifBlank { null },
            area = vod.area.orEmpty().ifBlank { null },
            remarks = vod.remarks.orEmpty().ifBlank { null },
            flags = vod.flags.map { flag ->
                DetailFlag(
                    name = flag.show.orEmpty(),
                    episodes = flag.episodes.map { DetailEpisode(it.name.orEmpty(), it.url.orEmpty()) },
                )
            },
        )
    }
}
