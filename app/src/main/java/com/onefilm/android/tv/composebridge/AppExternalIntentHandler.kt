package com.onefilm.android.tv.composebridge

import android.content.Context
import android.content.Intent
import com.onefilm.android.tv.compose.bridge.ExternalIntentHandler
import com.onefilm.android.tv.compose.bridge.ExternalPlaybackRequest
import com.onefilm.android.tv.utils.Sniffer

/**
 * 外部 SEND/VIEW 交给 Compose 新播放器：提取 URL 后写入 ExternalPlaybackRequest，
 * 由 ComposeMainActivity 消费并导航到 PlayerRoute（不再启动旧 VideoActivity）。
 */
class AppExternalIntentHandler : ExternalIntentHandler {
    override fun handle(context: Context, intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Sniffer.getUrl(it) }
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return

        ExternalPlaybackRequest.url = url
    }
}
