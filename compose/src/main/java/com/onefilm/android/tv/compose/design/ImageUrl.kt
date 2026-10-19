package com.onefilm.android.tv.compose.design

import android.net.Uri

data class ImageSource(
    val url: String,
    val referer: String?,
    val userAgent: String?,
)

/**
 * 解析配置源使用的 FongMi 图片头语法：
 * `https://...jpg@Referer=https://...@User-Agent=Mozilla%2F5.0...`
 */
fun parseImageSource(raw: String?): ImageSource? {
    if (raw.isNullOrBlank()) return null
    val url = raw.substringBefore("@Referer=").substringBefore("@User-Agent=")
    if (url.isBlank()) return null
    val referer = raw.substringAfter("@Referer=", "")
        .substringBefore("@User-Agent=")
        .takeIf { it.isNotBlank() }
        ?.let { Uri.decode(it) }
    val userAgent = raw.substringAfter("@User-Agent=", "")
        .takeIf { it.isNotBlank() }
        ?.let { Uri.decode(it) }
    return ImageSource(url, referer, userAgent)
}

fun cleanImageUrl(url: String?): String? = parseImageSource(url)?.url
