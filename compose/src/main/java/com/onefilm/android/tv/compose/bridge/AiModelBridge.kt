package com.onefilm.android.tv.compose.bridge

import androidx.compose.runtime.State

/**
 * AI 字幕模型仓库（下载 / 校验 / 安装 / 删除）。
 *
 * <p>LITE 不注入（保持 null），设置页因此不显示下载入口。
 */
interface AiModelBridge {

    /** 当前语种对应的数据集名（清单里的 `dataset`）。 */
    val dataset: String

    /** 展示名（清单里的 `label`）。 */
    val label: String

    /** 清单里的模型总字节数（未取到清单时为 0）。 */
    val totalBytes: Long

    /** 是否正在下载（跨面板生命周期：离开设置页再进来仍可见）。 */
    val downloading: State<Boolean>

    /** 下载进度 0..100。 */
    val progressPct: State<Int>

    /** 是否已安装且校验通过。 */
    fun isInstalled(): Boolean

    /** 已安装占用字节数。 */
    fun installedBytes(): Long

    /**
     * 开始下载（内部后台线程）。
     *
     * @param onProgress 进度回调（主线程）：已下载字节 / 总字节
     * @param onDone     结束回调（主线程）：成功与否 + 失败原因
     * @return 用于取消下载
     */
    fun download(
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onDone: (ok: Boolean, message: String?) -> Unit,
    ): AutoCloseable

    /** 删除已安装的模型。 */
    fun delete()
}
