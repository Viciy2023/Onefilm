package com.onefilm.android.tv.compose.design

import android.net.Uri
import org.json.JSONObject

data class ImageSource(
    val url: String,
    val headers: Map<String, String>,
)

/**
 * 解析配置源使用的图片头语法：
 * - `https://...jpg@Referer=https://...@User-Agent=Mozilla%2F5.0...`
 * - `https://...jpg@Headers={"Referer":"https://...","User-Agent":"Mozilla/5.0..."}`
 */
fun parseImageSource(raw: String?): ImageSource? {
    if (raw.isNullOrBlank()) return null
    val headers = linkedMapOf<String, String>()
    val headersIndex = raw.indexOf("@Headers=")
    if (headersIndex >= 0) {
        val url = raw.substring(0, headersIndex).trim()
        if (url.isBlank()) return null
        val json = raw.substring(headersIndex + "@Headers=".length)
        try {
            val objectValue = JSONObject(json)
            objectValue.keys().forEach { key ->
                val value = objectValue.optString(key, "")
                if (value.isNotBlank()) headers[key] = value
            }
        } catch (_: Exception) {
            // 配置源格式异常时至少去掉 @Headers= 后缀，避免把 JSON 当成 URL 的一部分。
        }
        applyImageHeaderFixes(url, headers)
        return ImageSource(url, headers)
    }

    val url = raw.substringBefore("@Referer=").substringBefore("@User-Agent=").trim()
    if (url.isBlank()) return null
    raw.substringAfter("@Referer=", "")
        .substringBefore("@User-Agent=")
        .takeIf { it.isNotBlank() }
        ?.let { headers["Referer"] = Uri.decode(it) }
    raw.substringAfter("@User-Agent=", "")
        .takeIf { it.isNotBlank() }
        ?.let { headers["User-Agent"] = Uri.decode(it) }
    applyImageHeaderFixes(url, headers)
    return ImageSource(url, headers)
}

private fun applyImageHeaderFixes(url: String, headers: MutableMap<String, String>) {
    val host = Uri.parse(url).host.orEmpty().lowercase()
    if (!host.endsWith("doubanio.com") && !host.endsWith("douban.com")) return
    val refererKey = headers.keys.firstOrNull { it.equals("Referer", true) } ?: "Referer"
    val currentReferer = headers[refererKey]
    if (currentReferer.isNullOrBlank() || !currentReferer.contains("douban.com", ignoreCase = true)) {
        headers[refererKey] = "https://movie.douban.com/"
    }
    if (headers.keys.none { it.equals("User-Agent", true) }) {
        headers["User-Agent"] =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/113.0.0.0 Safari/537.36"
    }
}

fun cleanImageUrl(url: String?): String? = parseImageSource(url)?.url
