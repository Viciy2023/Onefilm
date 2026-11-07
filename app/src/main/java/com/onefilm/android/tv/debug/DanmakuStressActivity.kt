package com.onefilm.android.tv.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.media3.ui.danmaku.DanmakuConfig
import androidx.media3.ui.danmaku.DanmakuItem
import androidx.media3.ui.danmaku.DanmakuView

/**
 * 弹幕渲染压测入口（临时，随 Step 10 移除）。
 *
 * adb shell am start -n com.onefilm.android.tv/.debug.DanmakuStressActivity \
 *   --el interval 50 --ei count 5000
 */
class DanmakuStressActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var position = 0L
    private var interval = 50L
    private lateinit var view: DanmakuView

    private val tick = object : Runnable {
        override fun run() {
            position += interval
            view.setClock(position)
            handler.postDelayed(this, interval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        interval = intent.getLongExtra("interval", 50L).coerceAtLeast(8L)
        val count = intent.getIntExtra("count", 5000).coerceIn(1, 50000)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = DanmakuView(this).apply {
            setConfig(
                DanmakuConfig.Builder()
                    .setTextScale(1f)
                    .setMaxScrollLines(8)
                    .setDurationMs(8000L)
                    .build(),
            )
            setItems(sample(count))
            setEnabled(true)
        }
        root.addView(view, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun sample(count: Int): List<DanmakuItem> {
        val list = ArrayList<DanmakuItem>(count)
        for (i in 0 until count) {
            val time = 200L + i * 100L
            val mode = when {
                i % 17 == 0 -> DanmakuItem.MODE_TOP
                i % 19 == 0 -> DanmakuItem.MODE_BOTTOM
                else -> DanmakuItem.MODE_SCROLL
            }
            val color = when {
                i % 5 == 0 -> 0xFFE8B45A.toInt()
                i % 7 == 0 -> 0xFF7FD1C8.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            list.add(DanmakuItem(time, mode, color, 25f, "OneFilm 弹幕压测 #$i"))
        }
        return list
    }
}
