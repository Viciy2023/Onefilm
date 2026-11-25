package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.api.config.VodConfig
import com.onefilm.android.tv.bean.Keep
import com.onefilm.android.tv.compose.bridge.FavoriteStore

class AppFavoriteStore : FavoriteStore {

    private fun key(key: String, id: String) = "$key@@@$id"

    override fun isFavorite(key: String, id: String): Boolean =
        Keep.getVod().any { it.siteKey == key && it.vodId == id }

    override fun toggle(key: String, id: String, siteName: String, title: String, posterUrl: String?) {
        val existing = Keep.getVod().firstOrNull { it.siteKey == key && it.vodId == id }
        if (existing != null) {
            existing.delete()
            return
        }
        Keep().apply {
            setKey(key(key, id))
            setSiteName(siteName.ifBlank { VodConfig.get().getSite(key)?.name.orEmpty() })
            setVodName(title)
            setVodPic(posterUrl.orEmpty())
        }.save(VodConfig.getCid())
    }
}
