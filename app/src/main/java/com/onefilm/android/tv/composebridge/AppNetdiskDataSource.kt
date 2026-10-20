package com.onefilm.android.tv.composebridge

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.onefilm.android.tv.App
import com.onefilm.android.tv.bean.StorageDrive
import com.onefilm.android.tv.compose.bridge.NetdiskDataSource
import com.onefilm.android.tv.compose.bridge.NetdiskDrive
import com.onefilm.android.tv.compose.bridge.NetdiskEntry
import com.onefilm.android.tv.compose.bridge.NetdiskPlayback
import com.onefilm.android.tv.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AppNetdiskDataSource : NetdiskDataSource {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val xmlType = "application/xml; charset=utf-8".toMediaType()

    private val dao get() = AppDatabase.get().storageDriveDao

    override fun listDrives(): List<NetdiskDrive> = dao.getAll().map { it.toBridge() }

    override fun saveDrive(drive: NetdiskDrive): NetdiskDrive {
        val entity = StorageDrive(drive.name, drive.type, drive.configJson).apply { id = drive.id }
        val id = if (drive.id > 0) {
            dao.update(entity)
            drive.id
        } else {
            dao.insert(entity)?.toInt() ?: 0
        }
        return entity.toBridge(id)
    }

    override fun deleteDrive(id: Int) {
        dao.delete(id)
    }

    override suspend fun listEntries(drive: NetdiskDrive, path: String, page: Int): List<NetdiskEntry> =
        withContext(Dispatchers.IO) {
            when (drive.type) {
                0 -> listLocal(drive, path)
                1 -> listWebDav(drive, path)
                2 -> listAlist(drive, path, page)
                else -> emptyList()
            }
        }

    override suspend fun resolve(drive: NetdiskDrive, entry: NetdiskEntry): NetdiskPlayback =
        withContext(Dispatchers.IO) {
            when (drive.type) {
                0 -> NetdiskPlayback(entry.fileUrl ?: entry.path)
                1 -> {
                    val config = config(drive)
                    val url = webDavUrl(config, entry.path)
                    NetdiskPlayback(url, webDavHeaders(config))
                }
                2 -> {
                    val config = config(drive)
                    val direct = entry.fileUrl.orEmpty().takeIf { it.isNotBlank() }
                    if (direct != null) NetdiskPlayback(direct)
                    else NetdiskPlayback(alistFileUrl(config, entry.path))
                }
                else -> throw IllegalStateException("不支持的网盘类型")
            }
        }

    private fun listLocal(drive: NetdiskDrive, path: String): List<NetdiskEntry> {
        val config = config(drive)
        val context = App.get()
        val treeUri = config.get("uri")?.asString.orEmpty()
        if (treeUri.isBlank()) throw IllegalStateException("本地目录未选择")
        val folder = if (path.isBlank()) {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        } else {
            DocumentFile.fromSingleUri(context, Uri.parse(path))
        } ?: throw IllegalStateException("无法打开本地目录")
        return folder.listFiles().map { file ->
            NetdiskEntry(
                name = file.name.orEmpty().ifBlank { "未命名" },
                path = file.uri.toString(),
                isDirectory = file.isDirectory,
                fileUrl = if (file.isFile) file.uri.toString() else null,
            )
        }.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
    }

    private fun listWebDav(drive: NetdiskDrive, path: String): List<NetdiskEntry> {
        val config = config(drive)
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        if (base.isBlank()) throw IllegalStateException("WebDAV 地址为空")
        val fullPath = joinPath(root, path)
        val url = if (fullPath.isBlank()) "$base/" else "$base/${encodePath(fullPath)}"
        val body = """<?xml version="1.0" encoding="utf-8" ?>
            <d:propfind xmlns:d="DAV:">
              <d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop>
            </d:propfind>""".trimIndent()
        val request = Request.Builder()
            .url(url)
            .method("PROPFIND", body.toRequestBody(xmlType))
            .header("Depth", "1")
            .apply { webDavHeaders(config).forEach { (k, v) -> header(k, v) } }
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("WebDAV 返回 ${it.code}")
            val text = it.body?.string().orEmpty()
            return parseWebDav(text, base, root, path)
        }
    }

    private fun parseWebDav(xml: String, base: String, root: String, parentPath: String): List<NetdiskEntry> {
        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val responses = doc.getElementsByTagNameNS("*", "response")
        val basePath = runCatching { URI(base).path.orEmpty().trimEnd('/') }.getOrDefault("")
        val currentPath = "/" + joinPath(root, parentPath).trim('/')
        val result = mutableListOf<NetdiskEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as? Element ?: continue
            val hrefElement = response.getElementsByTagNameNS("*", "href").item(0) as? Element ?: continue
            val href = hrefElement.textContent.orEmpty()
            val decodedPath = runCatching {
                URLDecoder.decode(URI(href).path.orEmpty(), "UTF-8")
            }.getOrDefault(href).trimEnd('/')
            if (decodedPath == currentPath) continue
            val relative = if (basePath.isNotBlank() && decodedPath.startsWith(basePath)) {
                decodedPath.removePrefix(basePath)
            } else {
                decodedPath
            }.trim('/')
            val rootPrefix = root.trim('/')
            val driveRelative = if (rootPrefix.isNotBlank() && relative.startsWith("$rootPrefix/")) {
                relative.removePrefix("$rootPrefix/")
            } else if (relative == rootPrefix) {
                ""
            } else {
                relative
            }
            if (driveRelative.isBlank()) continue
            val name = driveRelative.substringAfterLast('/')
            if (name.isBlank()) continue
            val isDirectory = response.getElementsByTagNameNS("*", "collection").length > 0
            result += NetdiskEntry(
                name = name,
                path = if (parentPath.isBlank()) name else "$parentPath/$name",
                isDirectory = isDirectory,
            )
        }
        return result.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
    }

    private fun listAlist(drive: NetdiskDrive, path: String, page: Int): List<NetdiskEntry> {
        val config = config(drive)
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        if (base.isBlank()) throw IllegalStateException("Alist 地址为空")
        val fullPath = "/" + joinPath(root, path)
        val payload = JsonObject().apply {
            addProperty("path", if (fullPath == "") "/" else fullPath)
            addProperty("password", config.get("password")?.asString.orEmpty())
            addProperty("page_num", page)
            addProperty("page_size", 100)
        }
        val request = Request.Builder()
            .url("$base/api/public/path")
            .post(payload.toString().toRequestBody(jsonType))
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("Alist 返回 ${it.code}")
            val rootJson = JsonParser.parseString(it.body?.string().orEmpty()).asJsonObject
            if (rootJson.get("code")?.asInt != 200) {
                throw IllegalStateException(rootJson.get("message")?.asString ?: "Alist 请求失败")
            }
            val data = rootJson.getAsJsonObject("data") ?: return emptyList()
            val files = data.getAsJsonArray("files") ?: data.getAsJsonArray("content")
                ?: return emptyList()
            val result = mutableListOf<NetdiskEntry>()
            files.forEach { item ->
                val obj = item.asJsonObject
                val name = obj.get("name")?.asString.orEmpty()
                if (name.isBlank()) return@forEach
                val isDirectory = obj.get("type")?.asInt == 1
                val directUrl = obj.get("url")?.asString?.takeIf { it.isNotBlank() }
                result += NetdiskEntry(
                    name = name,
                    path = if (path.isBlank()) name else "$path/$name",
                    isDirectory = isDirectory,
                    fileUrl = directUrl,
                )
            }
            return result.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
        }
    }

    private fun alistFileUrl(config: JsonObject, path: String): String {
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        val payload = JsonObject().apply {
            addProperty("path", "/" + joinPath(root, path))
            addProperty("password", config.get("password")?.asString.orEmpty())
        }
        val request = Request.Builder()
            .url("$base/api/fs/get")
            .post(payload.toString().toRequestBody(jsonType))
            .header("Accept", "application/json, text/plain, */*")
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException("Alist 返回 ${it.code}")
            val rootJson = JsonParser.parseString(it.body?.string().orEmpty()).asJsonObject
            if (rootJson.get("code")?.asInt != 200) {
                throw IllegalStateException(rootJson.get("message")?.asString ?: "无法获取文件地址")
            }
            return rootJson.getAsJsonObject("data")?.get("raw_url")?.asString
                ?: throw IllegalStateException("Alist 未返回 raw_url")
        }
    }

    private fun webDavUrl(config: JsonObject, path: String): String {
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        val full = joinPath(root, path)
        return if (full.isBlank()) "$base/" else "$base/${encodePath(full)}"
    }

    private fun webDavHeaders(config: JsonObject): Map<String, String> {
        val username = config.get("username")?.asString.orEmpty()
        val password = config.get("password")?.asString.orEmpty()
        if (username.isBlank() && password.isBlank()) return emptyMap()
        val token = android.util.Base64.encodeToString(
            "$username:$password".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        return mapOf("Authorization" to "Basic $token")
    }

    private fun config(drive: NetdiskDrive): JsonObject = runCatching {
        JsonParser.parseString(drive.configJson).asJsonObject
    }.getOrElse { JsonObject() }

    private fun joinPath(a: String, b: String): String =
        listOf(a.trim('/'), b.trim('/')).filter { it.isNotBlank() }.joinToString("/")

    private fun encodePath(path: String): String =
        path.split('/').filter { it.isNotBlank() }.joinToString("/") {
            URLEncoder.encode(it, "UTF-8").replace("+", "%20")
        }

    private fun StorageDrive.toBridge(id: Int = this.id) = NetdiskDrive(
        id = id,
        name = name.orEmpty(),
        type = type,
        configJson = configJson.orEmpty(),
    )
}
