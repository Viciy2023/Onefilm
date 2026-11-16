package com.onefilm.android.tv.composebridge

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onefilm.android.tv.compose.bridge.PanConfigActions
import com.onefilm.android.tv.compose.bridge.PanLogEntry
import com.onefilm.android.tv.compose.bridge.PanQr
import com.onefilm.android.tv.compose.bridge.PanQrState
import com.onefilm.android.tv.compose.bridge.WebConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 配置中心（spider 自带网盘面板）的 HTTP 接口。
 *
 * 页面地址形如 `http://<ip>:19989/config`，会 302 到
 * `http://<ip>:9978/proxy?do=pan&action=config`，接口基址就是 `/proxy?do=pan`。
 */
object AppPanConfig : PanConfigActions {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val plainClient = client.newBuilder().followRedirects(true).build()

    private val bases = mutableMapOf<String, String>()
    private var lastBase: String? = null
    private val tasks = mutableMapOf<String, String>()

    override suspend fun startLogin(siteKey: String, provider: String, title: String): PanQr? =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext null
            val json = get(base + "&action=loginStart&provider=" + provider) ?: return@withContext null
            if (json.get("code")?.asInt != 0) return@withContext null
            val taskId = json.get("taskId")?.asString.orEmpty()
            val qrUrl = json.get("qrUrl")?.asString.orEmpty()
            if (qrUrl.isBlank()) return@withContext null
            tasks[siteKey + "|" + provider] = taskId
            PanQr(title = title, message = json.get("msg")?.asString ?: "请使用 App 扫码确认", provider = provider, text = qrUrl)
        }

    override suspend fun pollLogin(siteKey: String, provider: String): PanQrState =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext PanQrState.Error("接口不可用")
            val taskId = tasks[siteKey + "|" + provider].orEmpty()
            val json = get(base + "&action=loginPoll&provider=" + provider + "&taskId=" + taskId)
                ?: return@withContext PanQrState.Waiting
            when {
                json.get("status")?.asString == "success" -> PanQrState.Success
                json.get("terminal")?.asBoolean == true ->
                    PanQrState.Error(json.get("msg")?.asString ?: "登录失败")
                else -> PanQrState.Waiting
            }
        }

    override suspend fun runAction(siteKey: String, action: String): Boolean =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext false
            get(base + "&" + action) != null
        }

    override suspend fun status(siteKey: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext emptyMap()
            val json = get(base + "&action=status") ?: return@withContext emptyMap()
            val result = mutableMapOf<String, String>()
            runCatching {
                json.entrySet().forEach { (name, element) ->
                    if (element == null || element.isJsonNull) return@forEach
                    val value = when {
                        element.isJsonArray -> element.asJsonArray.joinToString(",") { it.asString }
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                    if (value.isNotBlank()) result[name] = value
                }
            }
            result
        }

    override suspend fun logs(siteKey: String): List<PanLogEntry> =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext emptyList()
            val json = get(base + "&action=logList") ?: return@withContext emptyList()
            val array = json.getAsJsonArray("list") ?: return@withContext emptyList()
            runCatching {
                array.mapNotNull { element ->
                    val item = element.asJsonObject
                    val name = item.get("name")?.asString ?: return@mapNotNull null
                    PanLogEntry(
                        name = name,
                        size = item.get("sizeText")?.asString.orEmpty(),
                        time = item.get("modifiedText")?.asString.orEmpty(),
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun get(url: String): JsonObject? = runCatching {        val request = Request.Builder().url(url).get().build()
        val text = plainClient.newCall(request).execute().use { it.body?.string().orEmpty() }
        val trimmed = text.trim()
        val source = if (trimmed.startsWith("{") || trimmed.startsWith("[")) trimmed else String(Base64.decode(trimmed, Base64.DEFAULT))
        JsonParser.parseString(source).asJsonObject
    }.getOrNull()

    private fun base(siteKey: String): String? {
        bases[siteKey]?.let { return it }
        val page = WebConfigStore.get(siteKey) ?: return lastBase
        val request = Request.Builder().url(page).get().build()
        val location = runCatching {
            client.newCall(request).execute().use { it.header("Location").orEmpty() }
        }.getOrNull().orEmpty()
        val target = location.ifBlank { page }
        val cut = target.indexOf("&action=")
        val resolved = if (cut > 0) target.substring(0, cut) else target.substringBefore('?')
        if (!resolved.startsWith("http")) return lastBase
        bases[siteKey] = resolved
        lastBase = resolved
        return resolved
    }
}
