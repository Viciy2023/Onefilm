package com.onefilm.android.tv.compose.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onefilm.android.tv.compose.design.parseImageSource

/** 带 Referer/User-Agent 的网络海报，兼容配置源的 `@Referer=...@User-Agent=...` 后缀。 */
@Composable
fun RemoteImage(
    rawUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val source = remember(rawUrl) { parseImageSource(rawUrl) } ?: return
    val context = LocalContext.current
    val request = remember(source) {
        ImageRequest.Builder(context)
            .data(source.url)
            .apply {
                source.referer?.let { addHeader("Referer", it) }
                source.userAgent?.let { addHeader("User-Agent", it) }
            }
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}
