package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.FavoriteStore

class AppFavoriteStore : FavoriteStore {

    private fun key(key: String, id: String) = "$key@@@$id"

    override fun isFavorite(key: String, id: String): Boolean = Keep.find(key(key, id)) != null

    override fun toggle(key: String, id: String, siteName: String, title: String, posterUrl: String?) {
        val full = key(key, id)
        val existing = Keep.find(full)
        if (existing != null) {
            existing.delete()
            return
        }
        Keep().apply {
            setKey(full)
            setSiteName(siteName.ifBlank { VodConfig.get().getSite(key)?.name.orEmpty() })
            setVodName(title)
            setVodPic(posterUrl.orEmpty())
        }.save()
    }
}
