package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.SubtitleApi
import com.onefilm.android.tv.bean.SubtitleSearchItem
import com.onefilm.android.tv.compose.bridge.SubtitleCandidate
import com.onefilm.android.tv.compose.bridge.SubtitleRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 复用旧播放器的 assrt 字幕接口。 */
class AppSubtitleRepository : SubtitleRepository {

    override val available: Boolean get() = SubtitleApi.hasToken()

    override suspend fun search(keyword: String): List<SubtitleCandidate> =
        suspendCancellableCoroutine { cont ->
            SubtitleApi.search(
                keyword,
                0,
                { page ->
                    if (cont.isActive) cont.resume((page?.items ?: emptyList()).map(::toCandidate))
                },
                { if (cont.isActive) cont.resume(emptyList()) },
            )
            cont.invokeOnCancellation { SubtitleApi.cancel() }
        }

    override suspend fun files(id: Int): List<SubtitleCandidate> =
        suspendCancellableCoroutine { cont ->
            SubtitleApi.detail(
                id,
                { items -> if (cont.isActive) cont.resume(items.orEmpty().map(::toCandidate)) },
                { if (cont.isActive) cont.resume(emptyList()) },
            )
            cont.invokeOnCancellation { SubtitleApi.cancel() }
        }

    override suspend fun download(item: SubtitleCandidate): String? {
        val target = SubtitleSearchItem.from(item.id, item.name, item.url, item.lang) ?: return null
        return suspendCancellableCoroutine { cont ->
            SubtitleApi.loadSubtitle(
                target,
                { files ->
                    if (cont.isActive) cont.resume(files.orEmpty().firstOrNull()?.url)
                },
                { if (cont.isActive) cont.resume(null) },
            )
            cont.invokeOnCancellation { SubtitleApi.cancel() }
        }
    }

    override fun cancel() = SubtitleApi.cancel()

    private fun toCandidate(item: SubtitleSearchItem): SubtitleCandidate = SubtitleCandidate(
        id = item.id,
        name = item.name.orEmpty(),
        url = item.url.orEmpty(),
        lang = item.lang.orEmpty(),
    )
}
