package com.onefilm.android.tv.core.adblock

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 广告过滤成功通知（共享业务层，不含任何 UI）。
 *
 * 过滤钩子运行在 media3 loader 线程，监听器回调也在该线程；
 * UI 侧必须自行切回主线程后再更新界面。
 */
object AdFilterNotifier {

    fun interface Listener {
        /** @param removedSeconds 本次删除的广告总时长（秒）。 */
        fun onAdFiltered(removedSeconds: Double)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    fun addListener(listener: Listener) {
        listeners.addIfAbsent(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun notifyFiltered(removedSeconds: Double) {
        for (listener in listeners) {
            runCatching { listener.onAdFiltered(removedSeconds) }
        }
    }
}
