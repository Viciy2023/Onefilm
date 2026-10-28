package com.onefilm.android.tv.composebridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.github.catvod.utils.Prefers
import com.onefilm.android.tv.api.config.LiveConfig
import com.onefilm.android.tv.bean.Config
import com.onefilm.android.tv.compose.bridge.LiveLauncher
import com.onefilm.android.tv.impl.Callback

/** 直接打开旧版直播入口；先按设置页保存的直播接口加载 LiveConfig。 */
class AppLiveLauncher : LiveLauncher {

    override fun open(context: Context) {
        try {
            val saved = Prefers.getString("compose_live_url")
            val config = if (saved.isNullOrBlank()) {
                val custom = Config.getAll(1).firstOrNull {
                    !it.url.isNullOrBlank() && it.url != com.onefilm.android.tv.api.config.VodConfig.getUrl()
                } ?: run {
                    start(context)
                    return
                }
                Config.find(custom.url, 1)
            } else {
                Config.find(saved, 1)
            }
            val handler = Handler(Looper.getMainLooper())
            var entered = false
            val enter = Runnable {
                if (!entered) {
                    entered = true
                    handler.removeCallbacksAndMessages(null)
                    start(context)
                }
            }
            // 接口慢或失效时也必须进直播界面：最多等 3 秒就打开
            handler.postDelayed(enter, 3000)
            LiveConfig.load(config, object : Callback() {
                override fun success() {
                    config.update()
                    handler.post(enter)
                }

                override fun error(msg: String) {
                    handler.post(enter)
                }
            })
        } catch (t: Throwable) {
            Log.e(TAG, "open live failed", t)
            start(context)
        }
    }

    private fun start(context: Context) {
        try {
            val clazz = Class.forName("com.onefilm.android.tv.ui.activity.LiveActivity")
            clazz.getMethod("start", Context::class.java).invoke(null, context)
        } catch (t: Throwable) {
            // 反射入口被混淆裁掉时会走到这里，必须能在 logcat 里看到
            Log.e(TAG, "start LiveActivity failed", t)
        }
    }

    private companion object {
        const val TAG = "AppLiveLauncher"
    }
}
