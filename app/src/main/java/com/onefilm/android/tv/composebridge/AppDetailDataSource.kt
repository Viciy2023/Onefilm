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
        val cacheKey = "detail_${key}_${id}"
        DiskCache.read<DetailInfo>(cacheKey, 24 * 60 * 60_000L)?.let {
            DetailCache.put(it)
            return@withContext it
        }
        try {
            VodConfig.get().ensureLoaded()
            var result = SiteApi.detailContent(key, id)
            // 配置中心这类站点的条目不是一个影片，而是「网页配置 / 扫码登录 / 清除 Cookie」等动作：
            // spider 会返回 web 配置，先打开网页让用户操作，完成后重新取一次详情。
            val web = result.takeWeb()
            if (web != null) {
                AppWebLogin.login(web)
                result = SiteApi.detailContent(key, id)
            }
            val vod = result.vod.setFlags()
            android.util.Log.d("ComposeDetail", "loaded key=$key id=$id name=${vod.name} pic=${vod.pic} flags=${vod.flags.size}")
            val info = DetailInfo(
                key = key,
                id = id,
                title = vod.name.orEmpty().ifBlank { "" },
                posterUrl = vod.pic.orEmpty().ifBlank { null },
                landscapeUrl = vod.picSlide.orEmpty().ifBlank { null },
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
            DiskCache.write(cacheKey, info)
            info
        } catch (e: Exception) {
            android.util.Log.e("ComposeDetail", "load failed key=$key id=$id", e)
            throw e
        }
    }
}
