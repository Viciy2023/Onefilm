package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.compose.bridge.PlayOption
import com.onefilm.android.tv.compose.bridge.PlaybackResolver
import com.onefilm.android.tv.compose.bridge.ResolvedPlayback
import com.onefilm.android.tv.utils.Sniffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppPlaybackResolver : PlaybackResolver {
    override suspend fun resolve(key: String, flag: String, episodeUrl: String): ResolvedPlayback? =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = SiteApi.playerContent(key, flag, episodeUrl)
                val url = result.realUrl
                if (url.isBlank() || url.equals("null", ignoreCase = true)) return@runCatching null
                ResolvedPlayback(
                    url = url,
                    headers = result.header,
                    // 站点标记了 parse/jx，或者推送来的不是直链视频，都需要再过解析引擎
                    needParse = result.needParse() || (key == SiteApi.PUSH && isWebPage(url)),
                    options = optionsOf(result),
                )
            }.onFailure {
                android.util.Log.e("PlaybackResolver", "resolve failed", it)
            }.getOrNull()
        }

    /** 站点的多地址（清晰度/备用源）原样带出，供播放器切换。 */
    private fun optionsOf(result: com.onefilm.android.tv.bean.Result): List<PlayOption> {
        val values = runCatching { result.url.values }.getOrDefault(emptyList())
        val list = values.mapNotNull { value ->
            val v = value.v.orEmpty()
            if (v.isBlank()) null else PlayOption(value.n.orEmpty(), v)
        }
        return list
    }

    /** 推送来的 http(s) 地址不是直链视频时，按网页地址走解析引擎。 */
    private fun isWebPage(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        return runCatching { !Sniffer.isVideoFormat(url) }.getOrDefault(true)
    }
}
