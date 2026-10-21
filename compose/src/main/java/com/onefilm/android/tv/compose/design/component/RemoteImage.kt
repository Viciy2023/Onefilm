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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.SubcomposeAsyncImage
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
) {
    val source = remember(rawUrl) { parseImageSource(rawUrl) }
    if (source == null) {
        PosterLogoFallback(modifier)
        return
    }
    val context = LocalContext.current
    val request = remember(source) {
        ImageRequest.Builder(context)
            .data(source.url)
            .apply {
                source.headers.forEach { (name, value) -> addHeader(name, value) }
            }
            .crossfade(true)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = { PosterLogoFallback(Modifier.fillMaxSize()) },
        error = { PosterLogoFallback(Modifier.fillMaxSize()) },
        onError = { state ->
            android.util.Log.e(
                "RemoteImage",
                "poster failed: ${source.url}",
                state.result.throwable,
            )
        },
    )
}

@Composable
private fun PosterLogoFallback(modifier: Modifier = Modifier) {
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
