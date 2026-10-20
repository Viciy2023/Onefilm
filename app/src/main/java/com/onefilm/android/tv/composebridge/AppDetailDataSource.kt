package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.DetailDataSource
import com.onefilm.android.tv.compose.bridge.DetailEpisode
import com.onefilm.android.tv.compose.bridge.DetailFlag
import com.onefilm.android.tv.compose.bridge.DetailInfo
import com.onefilm.android.tv.compose.bridge.DetailCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppDetailDataSource : DetailDataSource {

    override suspend fun load(key: String, id: String): DetailInfo = withContext(Dispatchers.IO) {
        DetailCache.get(key, id)?.let { return@withContext it }
        try {
            VodConfig.get().ensureLoaded()
            val vod = SiteApi.detailContent(key, id).vod.setFlags()
            android.util.Log.d("ComposeDetail", "loaded key=$key id=$id name=${vod.name} pic=${vod.pic} flags=${vod.flags.size}")
            val info = DetailInfo(
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
                sourceName = vod.siteName.orEmpty().ifBlank { null },
                actor = vod.actor.orEmpty().ifBlank { null },
                director = vod.director.orEmpty().ifBlank { null },
                playUrl = vod.playUrl.orEmpty()
                    .ifBlank { vod.flags.firstOrNull()?.episodes?.firstOrNull()?.url.orEmpty() }
                    .takeIf { it.isNotBlank() },
            )
            DetailCache.put(info)
            info
        } catch (e: Exception) {
            android.util.Log.e("ComposeDetail", "load failed key=$key id=$id", e)
            throw e
        }
    }
}
