package com.onefilm.android.tv.mobileui.component

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.json.JSONObject

/**
 * Mobile-only image loader. Config sources attach image headers with suffixes such as
 * `@Referer=...@User-Agent=...`; Coil must receive the stripped URL and the headers.
 */
@Composable
fun MobileRemoteImage(
    rawUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    val source = remember(rawUrl) { parseMobileImageSource(rawUrl) }
    val request = remember(source) {
        source?.let {
            ImageRequest.Builder(context)
                .data(it.url)
                .apply { it.headers.forEach { (name, value) -> addHeader(name, value) } }
                .crossfade(false)
                .build()
        }
    }
    if (request == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Box(modifier) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onError = { state ->
                    android.util.Log.e(
                        "MobileRemoteImage",
                        "poster failed: ${source?.url}",
                        state.result.throwable,
                    )
                },
            )
        }
    }
}

private data class MobileImageSource(
    val url: String,
    val headers: Map<String, String>,
)

private fun parseMobileImageSource(raw: String?): MobileImageSource? {
    if (raw.isNullOrBlank()) return null
    val headers = linkedMapOf<String, String>()
    val headersIndex = raw.indexOf("@Headers=")
    if (headersIndex >= 0) {
        val url = raw.substring(0, headersIndex).trim()
        if (url.isBlank()) return null
        try {
            val objectValue = JSONObject(raw.substring(headersIndex + "@Headers=".length))
            objectValue.keys().forEach { key ->
                val value = objectValue.optString(key, "")
                if (value.isNotBlank()) headers[key] = value
            }
        } catch (_: Exception) {
            // Keep the stripped URL even when the config's JSON header suffix is malformed.
        }
        applyMobileImageHeaderFixes(url, headers)
        return MobileImageSource(url, headers)
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
    applyMobileImageHeaderFixes(url, headers)
    return MobileImageSource(url, headers)
}

private fun applyMobileImageHeaderFixes(url: String, headers: MutableMap<String, String>) {
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
