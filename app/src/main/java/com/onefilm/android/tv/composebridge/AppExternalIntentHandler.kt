package com.onefilm.android.tv.composebridge

import android.content.Context
import android.content.Intent
import com.onefilm.android.tv.api.SiteApi
import com.onefilm.android.tv.compose.bridge.ExternalIntentHandler
import com.onefilm.android.tv.ui.activity.VideoActivity
import com.onefilm.android.tv.utils.Sniffer

/**
 * 外部 SEND/VIEW 继续交给旧播放器，Compose 首页不再依赖 HomeActivity。
 * 普通 URL 直接以 push_agent 播放；本地 file/content URI 也走同一播放器入口。
 */
class AppExternalIntentHandler : ExternalIntentHandler {
    override fun handle(context: Context, intent: Intent) {
        val url = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { Sniffer.getUrl(it) }
            Intent.ACTION_VIEW -> intent.data?.toString()
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return

        val player = Intent(context, VideoActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("key", SiteApi.PUSH)
            putExtra("id", url)
            putExtra("name", url)
        }
        context.startActivity(player)
    }
}
