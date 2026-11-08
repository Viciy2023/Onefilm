package com.onefilm.android.tv.compose.bridge

/** 解析引擎条目：name 用于展示，god=true 表示配置里的「神解析」（并行尝试全部引擎）。 */
data class ParseEntry(val name: String, val god: Boolean = false)

/** 解析结果：成功时给出可播放直链与请求头，失败时 url 为空。 */
data class ParseOutcome(val url: String, val headers: Map<String, String> = emptyMap()) {
    val isSuccess: Boolean get() = url.isNotBlank()

    companion object {
        val failure = ParseOutcome("")
    }
}

/**
 * 网页地址解析：把站点播放页交给旧的 ParseJob（解析接口 / 站点脚本 / WebView 嗅探），
 * 换回可直接播放的直链。Compose 播放器只依赖这个接口。
 */
interface ParseEngine {
    /** 设备是否有可用 WebView；没有时解析接口仍可能工作，但网页嗅探不可用。 */
    val available: Boolean

    /** 配置里的解析引擎（含「神解析」）。 */
    fun engines(): List<ParseEntry>

    /**
     * 解析网页地址。
     *
     * @param engine 引擎名称；为空表示使用配置里的默认解析。
     */
    suspend fun parse(url: String, engine: String? = null): ParseOutcome
}
