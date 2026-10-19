package com.onefilm.android.tv.debug

import android.app.Activity
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.LinearLayout
import android.widget.TextView
import com.onefilm.android.tv.player.mpv.MpvRuntimeClient

/** Debug-only: main app binds the runtime APK and renders through libmpv. */
class MpvClientSmokeActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var client: MpvRuntimeClient
    private lateinit var surfaceView: SurfaceView
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        surfaceView = SurfaceView(this).apply { holder.addCallback(this@MpvClientSmokeActivity) }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(24, 24, 24, 24)
        }
        root.addView(surfaceView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(status, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        client = MpvRuntimeClient(this, object : MpvRuntimeClient.Listener {
            override fun onConnected() {
                status.text = "runtime connected"
                if (surfaceView.holder.surface?.isValid == true) start()
            }

            override fun onDisconnected() {
                status.text = "runtime disconnected"
            }
        })
        if (!client.bind()) status.text = "bind failed (runtime APK missing?)"
    }

    private fun start() {
        client.setSurface(surfaceView.holder.surface)
        client.open(URL)
        client.play()
        status.text = "playing via runtime: $URL"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (client.isConnected) start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        client.setSurface(null)
    }

    override fun onDestroy() {
        client.release()
        super.onDestroy()
    }

    private companion object {
        const val URL = "http://127.0.0.1:8000/sample-ch.mkv"
    }
}
