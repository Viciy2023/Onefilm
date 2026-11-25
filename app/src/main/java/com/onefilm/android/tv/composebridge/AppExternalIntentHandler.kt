package com.onefilm.android.tv.composebridge

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.onefilm.android.tv.R
import com.onefilm.android.tv.compose.bridge.ExternalIntentHandler
import com.onefilm.android.tv.compose.bridge.ExternalPlaybackRequest
import com.onefilm.android.tv.utils.Sniffer

/**
 * 外部 SEND/VIEW 交给 Compose 新播放器。
 *
 * - 本地 content/file URI：直接交给 Media3 播放；
 * - http(s) 地址：交给播放会话解析，网页地址会自动走解析引擎（解析接口 / WebView 嗅探）；
 * - 磁力/电驴/迅雷/push 协议：提示暂不支持。
 */
class AppExternalIntentHandler : ExternalIntentHandler {
    override fun handle(context: Context, intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Sniffer.getUrl(it) }
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return

        when {
            isLocalFile(url) -> ExternalPlaybackRequest.pending =
                ExternalPlaybackRequest.Request(url, direct = true)

            isNetworkLink(url) -> ExternalPlaybackRequest.pending =
                ExternalPlaybackRequest.Request(url)

            else -> Toast.makeText(context, R.string.external_link_unsupported, Toast.LENGTH_LONG).show()
        }
    }

    private fun isLocalFile(url: String): Boolean =
        url.startsWith("content://") || url.startsWith("file://")

    private fun isNetworkLink(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")
}
