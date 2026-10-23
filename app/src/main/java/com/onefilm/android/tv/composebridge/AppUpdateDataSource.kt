package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.App
import com.onefilm.android.tv.compose.bridge.UpdateDataSource
import com.onefilm.android.tv.compose.bridge.UpdateInfo
import com.onefilm.android.tv.setting.Setting
import com.onefilm.android.tv.utils.Download
import com.onefilm.android.tv.utils.FileUtil
import com.onefilm.android.tv.utils.Task
import com.onefilm.android.tv.utils.UpdateManifest
import com.github.catvod.utils.Path
import java.io.File

class AppUpdateDataSource : UpdateDataSource {

    private var download: Download? = null

    override fun check(onResult: (UpdateInfo?) -> Unit) {
        if (!Setting.getUpdate()) {
            onResult(null)
            return
        }
        Task.execute {
            val info = runCatching { UpdateManifest.fetch() }.getOrNull()
            val result = if (info != null && UpdateManifest.isNewer(info)) {
                UpdateInfo(
                    version = info.version,
                    notes = info.notes.ifBlank { "发现新版本，建议更新后再使用。" },
                    url = info.url,
                )
            } else {
                null
            }
            App.post { onResult(result) }
        }
    }

    override fun download(
        url: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        download?.cancel()
        val task = Download.create(url, Path.cache("update.apk"))
        download = task
        task.start(object : Download.Callback {
            override fun progress(progress: Int) {
                onProgress(progress)
            }

            override fun error(msg: String) {
                onError(msg)
            }

            override fun success(file: File) {
                FileUtil.openFile(file)
                onSuccess()
            }
        })
    }

    override fun cancelDownload() {
        download?.cancel()
        download = null
    }

    override fun ignore() {
        Setting.putUpdate(false)
    }
}
