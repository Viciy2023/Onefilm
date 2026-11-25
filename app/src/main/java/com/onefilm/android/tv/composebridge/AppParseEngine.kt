package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.Result
import com.onefilm.android.tv.compose.bridge.ParseEngine
import com.onefilm.android.tv.compose.bridge.ParseEntry
import com.onefilm.android.tv.compose.bridge.ParseOutcome
import com.onefilm.android.tv.impl.ParseCallback
import com.onefilm.android.tv.player.parse.ParseJob
import com.onefilm.android.tv.utils.WebViewUtil
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * ParseJob 的 Compose 适配层：复用旧播放器的整套解析能力
 * （json 解析接口、json 扩展、站点 JS、WebView 嗅探、神解析）。
 */
class AppParseEngine : ParseEngine {
    override val available: Boolean get() = WebViewUtil.support()

    override fun engines(): List<ParseEntry> =
        VodConfig.get().parses.map { ParseEntry(it.name, it.type == 4) }

    override suspend fun parse(
        url: String,
        engine: String?,
        headers: Map<String, String>,
        flag: String,
        siteKey: String,
    ): ParseOutcome {
        if (!available || url.isBlank()) return ParseOutcome.failure
        return suspendCancellableCoroutine { cont ->
            val resumed = AtomicBoolean()
            val job = ParseJob.create(object : ParseCallback {
                override fun onParseSuccess(headers: MutableMap<String, String>?, u: String?, from: String?) {
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(ParseOutcome(u.orEmpty(), headers.orEmpty()))
                    }
                }

                override fun onParseError() {
                    if (resumed.compareAndSet(false, true) && cont.isActive) {
                        cont.resume(ParseOutcome.failure)
                    }
                }
            })
            val result = Result()
            result.setUrl(url)
            // 旧播放器解析时会把站点上下文一起带进去，这里保持一致：
            // header → 解析请求头；key → 站点 click 脚本；flag → 神解析按线路筛引擎。
            if (headers.isNotEmpty()) result.setHeader(headers)
            if (siteKey.isNotBlank()) {
                result.setKey(siteKey)
                val click = runCatching { VodConfig.get().getSite(siteKey).getClick() }.getOrDefault("")
                if (click.isNotBlank()) result.setClick(click)
            }
            if (flag.isNotBlank()) result.setFlag(flag)
            if (!engine.isNullOrBlank()) {
                result.setPlayUrl("parse:$engine")
                // 与旧播放器一致：手动选中的引擎记为默认解析，下次播放沿用。
                runCatching {
                    val parse = VodConfig.get().getParse(engine)
                    if (parse != null && !parse.isEmpty) VodConfig.get().setParse(parse)
                }
            }
            cont.invokeOnCancellation { job.stop() }
            job.start(result, engine.isNullOrBlank())
        }
    }
}
