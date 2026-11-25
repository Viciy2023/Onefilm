package com.onefilm.android.tv.composebridge

import android.util.Base64
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onefilm.android.tv.compose.bridge.PanConfigActions
import com.onefilm.android.tv.compose.bridge.PanCommandResult
import com.onefilm.android.tv.compose.bridge.PanEmbyServer
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
        runCommand(siteKey, action).ok

    override suspend fun runCommand(siteKey: String, action: String): PanCommandResult =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext PanCommandResult(false, "接口不可用")
            val json = get(base + "&" + action)
                ?: return@withContext PanCommandResult(false, "请求失败，请检查接口是否在线")
            val code = json.get("code")?.asInt ?: 0
            val msg = json.get("msg")?.asString.orEmpty()
            PanCommandResult(ok = code == 0, message = msg.ifBlank { if (code == 0) "已保存" else "操作失败" })
        }

    override suspend fun status(siteKey: String): Map<String, String> =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext emptyMap()
            val json = get(base + "&action=status") ?: return@withContext emptyMap()
            val result = mutableMapOf<String, String>()
            runCatching {
                json.entrySet().forEach { (name, element) ->
                    if (element == null || element.isJsonNull) return@forEach
                    when {
                        // 嵌套对象压平成 name.sub 形式（例如 goProxy.host）
                        element.isJsonObject -> element.asJsonObject.entrySet().forEach { (sub, value) ->
                            if (value != null && !value.isJsonNull) result["$name.$sub"] = value.asString
                        }
                        element.isJsonArray -> result[name] = element.asJsonArray.joinToString(",") { item ->
                            if (item.isJsonPrimitive) item.asString else ""
                        }
                        element.isJsonPrimitive -> result[name] = element.asString
                        else -> result[name] = element.toString()
                    }
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

    override suspend fun embyServers(siteKey: String): List<PanEmbyServer> =
        withContext(Dispatchers.IO) {
            val base = base(siteKey) ?: return@withContext emptyList()
            val json = get(base + "&action=status") ?: return@withContext emptyList()
            val array = json.getAsJsonArray("embyServers") ?: return@withContext emptyList()
            runCatching {
                array.mapNotNull { element ->
                    val item = element.asJsonObject
                    PanEmbyServer(
                        name = item.get("servername")?.asString.orEmpty(),
                        url = item.get("url")?.asString.orEmpty(),
                        user = item.get("username")?.asString.orEmpty(),
                    )
                }
            }.getOrDefault(emptyList())
        }

    private fun get(url: String): JsonObject? = runCatching {
        val request = Request.Builder().url(url).get().build()
        val text = plainClient.newCall(request).execute().use { it.body?.string().orEmpty() }
        val trimmed = text.trim()
        val source = if (trimmed.startsWith("{") || trimmed.startsWith("[")) trimmed else String(Base64.decode(trimmed, Base64.DEFAULT))
        JsonParser.parseString(source).asJsonObject
    }.getOrNull()

    private fun base(siteKey: String): String? {
        bases[siteKey]?.let { return it }
        // 1) 本机 Webserver 端口：/config 的 302 目标就在这个端口上，最稳。
        //    配置页入口和接口都在 App 自己的本地 HTTP 服务里，127.0.0.1 一定可达。
        val local = runCatching {
            "http://127.0.0.1:" + com.github.catvod.Proxy.getPort() + "/proxy?do=pan"
        }.getOrNull()
        if (!local.isNullOrBlank() && probe(local)) return remember(siteKey, local)
        // 2) 退化：从卡片备注里的配置页地址跟随 302，取最终地址再截掉 action
        val page = WebConfigStore.get(siteKey) ?: return lastBase
        val finalUrl = runCatching {
            val request = Request.Builder().url(page).get().build()
            plainClient.newCall(request).execute().use { it.request.url.toString() }
        }.getOrNull().orEmpty().ifBlank { page }
        val cut = finalUrl.indexOf("&action=")
        val resolved = if (cut > 0) finalUrl.substring(0, cut) else finalUrl
        if (!resolved.startsWith("http")) return lastBase
        return remember(siteKey, resolved)
    }

    private fun remember(siteKey: String, base: String): String {
        bases[siteKey] = base
        lastBase = base
        return base
    }

    /** 探一下这个基址能不能用：能返回 `code:0` 才算数。 */
    private fun probe(base: String): Boolean = runCatching {
        get(base + "&action=status")?.get("code")?.asInt == 0
    }.getOrDefault(false)
}
