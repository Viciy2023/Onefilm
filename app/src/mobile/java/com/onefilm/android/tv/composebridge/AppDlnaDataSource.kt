package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.App
import com.onefilm.android.tv.bean.CastVideo
import com.onefilm.android.tv.compose.bridge.DlnaDataSource
import com.onefilm.android.tv.compose.bridge.DlnaDevice
import com.onefilm.android.tv.dlna.DLNACast
import com.onefilm.android.tv.dlna.DLNACastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class AppDlnaDataSource : DlnaDataSource {

    override suspend fun devices(): List<DlnaDevice> = withContext(Dispatchers.Main) {
        val manager = DLNACastManager.get()
        manager.init(App.get())
        manager.search()
        delay(1_800)
        manager.registered.map { DlnaDevice(it.uuid.orEmpty(), it.name.orEmpty()) }
    }

    override fun cast(
        deviceId: String,
        title: String,
        url: String,
        positionMs: Long,
        headers: Map<String, String>,
    ) {
        val manager = DLNACastManager.get()
        val device = manager.registered.firstOrNull { it.uuid == deviceId } ?: return
        DLNACast(CastVideo(title, url, positionMs, headers), Runnable {}).cast(device)
    }
}
