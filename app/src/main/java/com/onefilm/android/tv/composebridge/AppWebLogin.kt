package com.onefilm.android.tv.composebridge

import android.app.Activity
import android.content.Intent
import com.onefilm.android.tv.App
import com.onefilm.android.tv.bean.Web
import com.onefilm.android.tv.ui.activity.WebLoginActivity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 站点要求网页登录时的入口：打开登录页并等待结果。
 *
 * 调用方（播放解析链路）挂起等待，登录页关闭后恢复，便于「登录完自动重试播放」。
 */
object AppWebLogin {

    private const val TIMEOUT_MS = 5 * 60 * 1000L

    private val lock = Any()
    private var pending: CompletableDeferred<Boolean>? = null

    /** @return true 表示检测到登录完成（可以重试播放），false 表示用户退出、超时或启动失败。 */
    suspend fun login(web: Web, selectCard: String? = null, compact: Boolean = false): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        synchronized(lock) {
            pending?.complete(false)
            pending = deferred
        }
        val started = withContext(Dispatchers.Main) {
            runCatching {
                val context = App.activity()
                val intent = Intent(App.get(), loginActivityClass())
                    .putExtra(WebLoginActivity.EXTRA_WEB, App.gson().toJson(web))
                if (!selectCard.isNullOrBlank()) {
                    intent.putExtra(WebLoginActivity.EXTRA_SELECT, selectCard)
                }
                if (compact) intent.putExtra(WebLoginActivity.EXTRA_COMPACT, true)
                if (context == null || context.isFinishing || context.isDestroyed) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    App.get().startActivity(intent)
                } else {
                    context.startActivity(intent)
                }
            }.isSuccess
        }
        if (!started) {
            complete(false)
            return false
        }
        return withTimeoutOrNull(TIMEOUT_MS) { deferred.await() } ?: false
    }

    /** 登录页关闭时回调；重复调用只有第一次生效。 */
    fun complete(success: Boolean) {
        val deferred = synchronized(lock) {
            val value = pending
            pending = null
            value
        }
        deferred?.complete(success)
    }

    /** TV / 手机各自实现登录页，这里按注册顺序取第一个存在的。 */
    fun loginActivityClass(): Class<out Activity> {
        val candidates = arrayOf(
            "com.onefilm.android.tv.ui.activity.TvWebLoginActivity",
            "com.onefilm.android.tv.ui.activity.MobileWebLoginActivity",
        )
        for (name in candidates) {
            val type = runCatching { Class.forName(name) }.getOrNull() ?: continue
            val activity = runCatching { type.asSubclass(Activity::class.java) }.getOrNull() ?: continue
            return activity
        }
        return WebLoginActivity::class.java
    }
}
