package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.design.parseImageSource

/** 带 Referer/User-Agent 的网络海报，兼容配置源的 `@Referer=...@User-Agent=...` 后缀。 */
@Composable
fun RemoteImage(
    rawUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    requestWidth: Int? = null,
    requestHeight: Int? = null,
    showPlaceholder: Boolean = true,
    onSize: ((Int, Int) -> Unit)? = null,
) {
    val source = remember(rawUrl) { parseImageSource(rawUrl) }
    // 接口有时直接把海报做成 base64 的 data URI，Coil 没有对应的 fetcher，这里自己解码
    if (!rawUrl.isNullOrBlank() && rawUrl.startsWith("data:image", ignoreCase = true)) {
        val bitmap = remember(rawUrl) { decodeDataUri(rawUrl) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = modifier.fillMaxSize(),
            )
            return
        }
        PosterLogoFallback(modifier)
        return
    }
    if (source == null) {
        PosterLogoFallback(modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(source, requestWidth, requestHeight) {
        ImageRequest.Builder(context)
            .data(source.url)
            .apply {
                source.headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .crossfade(false)
            .apply {
                if (requestWidth != null && requestHeight != null) size(requestWidth, requestHeight)
            }
            .build()
    }
    Box(modifier) {
        if (showPlaceholder) PosterLogoFallback(Modifier.fillMaxSize())
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                val drawable = state.result.drawable
                if (drawable != null) {
                    onSize?.invoke(drawable.intrinsicWidth, drawable.intrinsicHeight)
                }
            },
            onError = { state ->
                android.util.Log.e(
                    "RemoteImage",
                    "poster failed: ${source.url}",
                    state.result.throwable,
                )
            },
        )
    }
}

@Composable
internal fun PosterLogoFallback(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.app_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(0.55f),
        )
    }
}

/** 解码 `data:image/...;base64,xxxx` 形式的海报；解不出来返回 null。 */
internal fun decodeDataUri(uri: String): android.graphics.Bitmap? = runCatching {
    val comma = uri.indexOf(',')
    if (comma <= 0) return null
    val bytes = android.util.Base64.decode(uri.substring(comma + 1), android.util.Base64.DEFAULT)
    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
