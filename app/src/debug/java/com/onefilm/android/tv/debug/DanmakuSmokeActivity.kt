package com.onefilm.android.tv.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.media3.ui.danmaku.DanmakuConfig
import androidx.media3.ui.danmaku.DanmakuParser
import androidx.media3.ui.danmaku.DanmakuView

/** Debug-only danmaku engine smoke test with a synthetic clock. */
class DanmakuSmokeActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var position = 0L

    private val tick = object : Runnable {
        override fun run() {
            position += 16
            view.setClock(position)
            handler.postDelayed(this, 16)
        }
    }

    private lateinit var view: DanmakuView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        view = DanmakuView(this).apply {
            setConfig(DanmakuConfig.Builder().setTextScale(1.0f).setMaxScrollLines(8).build())
            setItems(DanmakuParser.parseXml(sample()))
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

    private fun sample(): String = buildString {
        append("<i>")
        for (i in 0 until 80) {
            val time = 0.2 + i * 0.25
            val mode = when {
                i % 11 == 0 -> 5
                i % 13 == 0 -> 4
                else -> 1
            }
            val color = when {
                i % 5 == 0 -> 0xFFE8B45A
                i % 7 == 0 -> 0xFF7FD1C8
                else -> 0xFFFFFFFF
            }
            append("<d p=\"$time,$mode,25,${color and 0xFFFFFF},0,0,0,0\">OneFilm danmaku #$i 弹幕渲染测试</d>")
        }
        append("</i>")
    }
}
