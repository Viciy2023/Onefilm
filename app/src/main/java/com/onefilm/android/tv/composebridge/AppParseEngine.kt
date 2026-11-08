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

    override suspend fun parse(url: String, engine: String?): ParseOutcome {
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
            // 「parse:名称」让 ParseJob 选中指定引擎；留空则用配置里的默认解析。
            if (!engine.isNullOrBlank()) result.setPlayUrl("parse:$engine")
            cont.invokeOnCancellation { job.stop() }
            job.start(result, engine.isNullOrBlank())
        }
    }
}
