package com.onefilm.android.tv.mobileui.component

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Mobile-only image loader; does not reuse the TV RemoteImage component. */
@Composable
fun MobileRemoteImage(
    rawUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val request = remember(rawUrl) {
        rawUrl?.takeIf { it.isNotBlank() }?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(false)
                .build()
        }
    }
    if (request == null) {
        androidx.compose.foundation.layout.Box(
            modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        )
    } else {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
