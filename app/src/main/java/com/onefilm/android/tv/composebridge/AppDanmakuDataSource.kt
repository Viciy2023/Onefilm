package com.onefilm.android.tv.composebridge

import com.github.catvod.net.OkHttp
import com.onefilm.android.tv.api.DanmakuApi
import com.onefilm.android.tv.bean.Danmaku
import com.onefilm.android.tv.compose.bridge.DanmakuDataSource
import com.onefilm.android.tv.compose.bridge.DanmakuDisplayConfig
import com.onefilm.android.tv.compose.bridge.DanmakuSource
import com.onefilm.android.tv.setting.DanmakuSetting
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AppDanmakuDataSource : DanmakuDataSource {

    override suspend fun search(name: String, episode: String): List<DanmakuSource> =
        suspendCancellableCoroutine { continuation ->
            DanmakuApi.search(
                name,
                episode,
                { items: List<Danmaku> ->
                    if (continuation.isActive) {
                        continuation.resume(items.map { DanmakuSource(it.name, it.url) })
                    }
                },
                {
                    if (continuation.isActive) continuation.resume(emptyList())
                },
            )
        }

    override suspend fun load(url: String): String? = runCatching {
        OkHttp.string(url)
    }.getOrNull()?.takeIf { it.isNotBlank() }

    override fun displayConfig(): DanmakuDisplayConfig = DanmakuDisplayConfig(
        show = DanmakuSetting.isShow(),
        scale = DanmakuSetting.getTextScale(),
        transparency = DanmakuSetting.getTransparency(),
        durationMs = DanmakuSetting.getDurationMs(),
        lines = DanmakuSetting.getMaxScrollLines(),
    )
}
