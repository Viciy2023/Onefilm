package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.bean.Vod
import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.db.AppDatabase

object HomeFeedMapper {

    fun fromVods(vods: List<Vod>, siteKey: String): List<MediaCard> = vods.map { vod ->
        MediaCard(
            key = siteKey,
            id = vod.id.orEmpty(),
            title = vod.name.orEmpty().ifBlank { "未命名" },
            subtitle = vod.remarks.orEmpty().ifBlank { null },
            posterUrl = vod.pic.orEmpty().ifBlank { null },
            siteName = runCatching { VodConfig.get().getSite(siteKey).name }.getOrNull(),
        )
    }

    fun fromHistory(items: List<History>, limit: Int = 10): List<MediaCard> = items.take(limit).map { item ->
        val progress = if (item.duration > 0) item.position.toFloat() / item.duration else null
        MediaCard(
            key = item.siteKey.orEmpty(),
            id = item.vodId.orEmpty(),
            title = item.vodName.orEmpty().ifBlank { "未命名" },
            subtitle = item.vodRemarks.orEmpty().ifBlank { null },
            posterUrl = item.vodPic.orEmpty().ifBlank { null },
            progress = progress,
        )
    }

    fun fromKeeps(items: List<Keep>, limit: Int = 10): List<MediaCard> = items.take(limit).map { item ->
        val parts = item.key.split(AppDatabase.SYMBOL)
        MediaCard(
            key = parts.getOrNull(0).orEmpty(),
            id = parts.getOrNull(1).orEmpty(),
            title = item.vodName.orEmpty().ifBlank { "未命名" },
            subtitle = item.siteName.orEmpty().ifBlank { null },
            posterUrl = item.vodPic.orEmpty().ifBlank { null },
        )
    }
}
