package com.onefilm.android.tv.composebridge

import android.content.Context
import com.github.catvod.utils.Prefers
import com.onefilm.android.tv.api.config.LiveConfig
import com.onefilm.android.tv.bean.Config
import com.onefilm.android.tv.compose.bridge.LiveLauncher
import com.onefilm.android.tv.impl.Callback

/** 直接打开旧版直播入口；先按设置页保存的直播接口加载 LiveConfig。 */
class AppLiveLauncher : LiveLauncher {

    override fun open(context: Context) {
        val saved = Prefers.getString("compose_live_url")
        val config = if (saved.isNullOrBlank()) {
            val custom = Config.getAll(1).firstOrNull {
                !it.url.isNullOrBlank() && it.url != com.onefilm.android.tv.api.config.VodConfig.getUrl()
            }
            if (custom == null) {
                start(context)
                return
            }
            Config.find(custom.url, 1)
        } else {
            Config.find(saved, 1)
        }
        LiveConfig.load(config, object : Callback() {
            override fun success() {
                config.update()
                start(context)
            }

            override fun error(msg: String) {
                start(context)
            }
        })
    }

    private fun start(context: Context) {
        runCatching {
            val clazz = Class.forName("com.onefilm.android.tv.ui.activity.LiveActivity")
            clazz.getMethod("start", Context::class.java).invoke(null, context)
        }
    }
}
