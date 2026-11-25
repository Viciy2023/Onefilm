package com.onefilm.android.tv.compose.bridge

/**
 * 当前播放请求头（Referer / User-Agent / Cookie 等）。
 *
 * 广告过滤的 PTS 探测走独立的 OkHttp 请求，需要复用这些头，否则部分 CDN 会直接
 * 返回 403 或超时，导致探测拿不到 PTS、广告无法识别。
 */
object PlaybackHttpHeaders {
    @Volatile
    var headers: Map<String, String> = emptyMap()
}
