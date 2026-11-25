package com.onefilm.android.tv.composebridge

import com.github.catvod.net.OkHttp
import com.onefilm.android.tv.api.DanmakuApi
import com.onefilm.android.tv.bean.Danmaku
import com.onefilm.android.tv.compose.bridge.DanmakuDataSource
import com.onefilm.android.tv.compose.bridge.DanmakuDisplayConfig
import com.onefilm.android.tv.compose.bridge.DanmakuSource
import com.onefilm.android.tv.player.danmaku.DanmakuResolver
import com.onefilm.android.tv.setting.DanmakuSetting
import androidx.media3.ui.danmaku.DanmakuConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class AppDanmakuDataSource : DanmakuDataSource {

    private companion object {
        /** 服务端冷抓取实测 6~72 秒，不能用默认 30 秒客户端。 */
        const val LOAD_TIMEOUT_MS = 45_000L
    }

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

    override suspend fun load(url: String): String? = withContext(Dispatchers.IO) {
        try {
            OkHttp.newCall(OkHttp.client(LOAD_TIMEOUT_MS), url).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (t: Throwable) {
            android.util.Log.w("AppDanmaku", "load failed: $url", t)
            null
        }
    }

    override fun displayConfig(): DanmakuDisplayConfig = DanmakuDisplayConfig(
        show = DanmakuSetting.isShow(),
        scale = DanmakuSetting.getTextScale(),
        transparency = DanmakuSetting.getTransparency(),
        durationMs = DanmakuSetting.getDurationMs(),
        lines = DanmakuSetting.getMaxScrollLines(),
        blockWords = (com.github.catvod.utils.Prefers.getString("compose_danmaku_block_words") ?: "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        load = DanmakuSetting.isLoad(),
        auto = DanmakuSetting.isAuto(),
        apiConfigured = DanmakuSetting.getEffectiveApiUrl().isNotBlank(),
    )

    override fun engineConfig(): DanmakuConfig = DanmakuSetting.getConfig()

    override fun cancel() {
        DanmakuApi.cancel()
    }

    override fun invalidateCache() {
        DanmakuResolver.resetCache()
    }
}
