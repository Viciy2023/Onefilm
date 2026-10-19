package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.History
import com.onefilm.android.tv.compose.bridge.PlaybackHistoryInfo
import com.onefilm.android.tv.compose.bridge.PlaybackHistoryStore

class AppPlaybackHistoryStore : PlaybackHistoryStore {

    override fun find(key: String, id: String): PlaybackHistoryInfo? {
        val history = History.find(fullKey(key, id)) ?: return null
        return PlaybackHistoryInfo(
            positionMs = history.position.coerceAtLeast(0L),
            durationMs = history.duration.coerceAtLeast(0L),
            episodeUrl = history.episodeUrl,
            speed = history.speed.takeIf { it > 0f } ?: 1f,
        )
    }

    override fun save(
        key: String,
        id: String,
        title: String,
        poster: String?,
        flag: String,
        episodeName: String,
        episodeUrl: String,
        positionMs: Long,
        durationMs: Long,
        speed: Float,
    ) {
        if (positionMs <= 0L || durationMs <= 0L) return
        val cid = VodConfig.getCid()
        val fullKey = fullKey(key, id)
        val history = History.find(fullKey) ?: History().apply {
            setKey(fullKey)
            setCid(cid)
        }
        history.apply {
            setVodName(title)
            setVodPic(poster)
            setVodFlag(flag)
            setVodRemarks(episodeName)
            setEpisodeUrl(episodeUrl)
            setPosition(positionMs)
            setDuration(durationMs)
            setCreateTime(System.currentTimeMillis())
            setSpeed(speed)
        }
        history.save()
    }

    private fun fullKey(key: String, id: String): String =
        "$key@@@$id@@@${VodConfig.getCid()}"
}
