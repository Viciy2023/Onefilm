package com.onefilm.android.tv.composebridge

import com.onefilm.android.tv.App
import com.onefilm.android.tv.bean.Word
import com.onefilm.android.tv.compose.bridge.SearchMetaDataSource
import com.onefilm.android.tv.setting.Setting
import com.onefilm.android.tv.utils.ZhuToPin
import com.github.catvod.net.OkHttp
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class AppSearchMetaDataSource : SearchMetaDataSource {

    override fun history(): List<String> = runCatching {
        App.gson().fromJson<List<String>>(
            Setting.getKeyword(),
            TypeToken.getParameterized(List::class.java, String::class.java).type,
        ).orEmpty()
    }.getOrDefault(emptyList())

    override fun hot(): List<String> = runCatching {
        Word.objectFrom(Setting.getHot()).data.map { it.title }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    override suspend fun refreshHot(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://api.web.360kan.com/v1/rank?cat=1"
            OkHttp.newCall(url).execute().use { response ->
                val body = response.body.string()
                Setting.putHot(body)
                Word.objectFrom(body).data.map { it.title }.filter { it.isNotBlank() }
            }
        }.getOrDefault(hot())
    }

    override suspend fun suggest(text: String): List<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()
        runCatching {
            val url = "https://suggest.video.iqiyi.com/?if=mobile&key=" +
                URLEncoder.encode(ZhuToPin.get(text), "UTF-8")
            OkHttp.newCall(url).execute().use { response ->
                Word.objectFrom(response.body.string()).data.map { it.title }.filter { it.isNotBlank() }
            }
        }.getOrDefault(emptyList())
    }
}
