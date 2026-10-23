package com.onefilm.android.tv.compose.bridge

data class UpdateInfo(
    val version: String,
    val notes: String,
    val url: String,
)

/**
 * Compose 宿主通过这个桥接层检查/下载更新，避免 compose 模块反向依赖 app 模块。
 */
interface UpdateDataSource {
    fun check(onResult: (UpdateInfo?) -> Unit)
    fun download(
        url: String,
        onProgress: (Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    )
    fun cancelDownload()
    fun ignore()
}
