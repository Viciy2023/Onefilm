package com.onefilm.android.tv.composebridge

import android.content.Context
import com.onefilm.android.tv.compose.R

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
import com.github.catvod.utils.Prefers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppNetdiskDataSource(private val context: Context) : NetdiskDataSource {

    private companion object {
        /** RFC 1123（WebDAV）与 ISO8601（AList/OpenList）两种时间格式。 */
        val datePatterns = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd HH:mm:ss",
        )
    }

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

    override fun sortKey(): Int = Prefers.getInt("netdisk_sort_key", 0)

    override fun setSortKey(key: Int) {
        Prefers.put("netdisk_sort_key", key.coerceIn(0, 3))
    }

    override fun sortAscending(): Boolean = Prefers.getBoolean("netdisk_sort_asc", true)

    override fun setSortAscending(ascending: Boolean) {
        Prefers.put("netdisk_sort_asc", ascending)
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
                    NetdiskPlayback(webDavUrl(config, entry.path), webDavHeaders(config))
                }
                2 -> {
                    val config = config(drive)
                    val direct = entry.fileUrl.orEmpty().takeIf { it.isNotBlank() }
                    if (direct != null) NetdiskPlayback(direct)
                    else NetdiskPlayback(alistFileUrl(config, entry.path))
                }
                else -> throw IllegalStateException(context.getString(R.string.netdisk_err_type))
            }
        }

    private fun listLocal(drive: NetdiskDrive, path: String): List<NetdiskEntry> {
        val config = config(drive)
        val context = App.get()
        val treeUri = config.get("uri")?.asString.orEmpty()
        if (treeUri.isBlank()) throw IllegalStateException(context.getString(R.string.netdisk_err_no_local_dir))

        // 内置目录选择器保存的是 file:// 绝对路径，直接走文件系统，避免依赖 SAF。
        val rawPath = path.ifBlank { treeUri }
        val fsPath = rawPath.removePrefix("file://")
        if (fsPath.startsWith("/")) {
            val folder = File(fsPath)
            if (!folder.exists() || !folder.isDirectory) {
                throw IllegalStateException(context.getString(R.string.netdisk_err_open_local_dir))
            }
            return folder.listFiles().orEmpty().map { file ->
                val fileUrl = "file://" + file.absolutePath
                NetdiskEntry(
                    name = file.name.orEmpty().ifBlank { context.getString(R.string.detail_unnamed) },
                    path = fileUrl,
                    isDirectory = file.isDirectory,
                    fileUrl = if (file.isFile) fileUrl else null,
                    size = if (file.isFile) file.length() else 0L,
                    modified = file.lastModified(),
                )
            }.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
        }

        val folder = if (path.isBlank()) {
            DocumentFile.fromTreeUri(context, Uri.parse(treeUri))
        } else {
            DocumentFile.fromSingleUri(context, Uri.parse(path))
        } ?: throw IllegalStateException(context.getString(R.string.netdisk_err_open_local_dir))
        return folder.listFiles().map { file ->
            NetdiskEntry(
                name = file.name.orEmpty().ifBlank { context.getString(R.string.detail_unnamed) },
                path = file.uri.toString(),
                isDirectory = file.isDirectory,
                fileUrl = if (file.isFile) file.uri.toString() else null,
                size = if (file.isFile) file.length() else 0L,
                modified = file.lastModified(),
            )
        }.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
    }

    private fun listWebDav(drive: NetdiskDrive, path: String): List<NetdiskEntry> {
        val config = config(drive)
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        if (base.isBlank()) throw IllegalStateException(context.getString(R.string.netdisk_err_webdav_url))
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
            if (!it.isSuccessful) throw IllegalStateException(context.getString(R.string.netdisk_err_webdav_code, it.code))
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
            val sizeText = response.getElementsByTagNameNS("*", "getcontentlength").item(0)?.textContent
            val modifiedText = response.getElementsByTagNameNS("*", "getlastmodified").item(0)?.textContent
            result += NetdiskEntry(
                name = name,
                path = if (parentPath.isBlank()) name else "$parentPath/$name",
                isDirectory = isDirectory,
                size = if (isDirectory) 0L else sizeText?.trim()?.toLongOrNull() ?: 0L,
                modified = parseHttpDate(modifiedText),
            )
        }
        return result.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
    }

    private fun listAlist(drive: NetdiskDrive, path: String, page: Int): List<NetdiskEntry> {
        val config = config(drive)
        val base = config.get("url")?.asString.orEmpty().trimEnd('/')
        val root = config.get("initPath")?.asString.orEmpty().trim('/')
        if (base.isBlank()) throw IllegalStateException(context.getString(R.string.netdisk_err_alist_url))
        val fullPath = "/" + joinPath(root, path)
        val payload = JsonObject().apply {
            addProperty("path", if (fullPath == "") "/" else fullPath)
            addProperty("password", config.get("password")?.asString.orEmpty())
            addProperty("page", page.coerceAtLeast(1))
            addProperty("per_page", 100)
            addProperty("refresh", false)
        }
        val request = Request.Builder()
            .url("$base/api/fs/list")
            .post(payload.toString().toRequestBody(jsonType))
            .header("Accept", "application/json, text/plain, */*")
            .apply {
                val token = config.get("token")?.asString.orEmpty().trim()
                if (token.isNotEmpty()) header("Authorization", token)
            }
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException(context.getString(R.string.netdisk_err_alist_code, it.code))
            val rootJson = alistJson(it.body?.string().orEmpty())
            when (rootJson.get("code")?.asInt ?: 0) {
                200 -> Unit
                401 -> throw IllegalStateException(context.getString(R.string.netdisk_err_alist_auth))
                else -> throw IllegalStateException(
                    rootJson.get("message")?.asString?.takeIf { message -> message.isNotBlank() }
                        ?: context.getString(R.string.netdisk_err_alist_failed),
                )
            }
            val dataElement = rootJson.get("data")
            if (dataElement == null || !dataElement.isJsonObject) return emptyList()
            val data = dataElement.asJsonObject
            // content/files 可能是 null（空目录或服务端异常），不能直接 getAsJsonArray。
            val files = data.get("content")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: data.get("files")?.takeIf { it.isJsonArray }?.asJsonArray
                ?: return emptyList()
            val result = mutableListOf<NetdiskEntry>()
            files.forEach { item ->
                val obj = item.asJsonObject
                val name = obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                if (name.isBlank()) return@forEach
                // OpenList / AList v3 用 is_dir；兼容旧版返回的 type(1=目录)。
                val isDirectory = obj.get("is_dir")?.takeIf { !it.isJsonNull }?.asBoolean
                    ?: (obj.get("type")?.takeIf { !it.isJsonNull }?.asInt == 1)
                val directUrl = obj.get("url")?.asString?.takeIf { it.isNotBlank() }
                result += NetdiskEntry(
                    name = name,
                    path = if (path.isBlank()) name else "$path/$name",
                    isDirectory = isDirectory,
                    fileUrl = directUrl,
                    size = if (isDirectory) 0L else obj.get("size")?.takeIf { !it.isJsonNull }?.asLong ?: 0L,
                    modified = parseIsoTime(obj.get("modified")?.asString),
                )
            }
            return result.sortedWith(compareByDescending<NetdiskEntry> { it.isDirectory }.thenBy { it.name })
        }
    }

    /**
     * 解析 AList / OpenList 的 JSON 响应。
     *
     * 路由不存在时 OpenList 会回落到前端 index.html 并返回 200，直接交给 Gson 只会抛
     * MalformedJsonException；这里统一换成可读的错误提示。
     */
    private fun alistJson(text: String): JsonObject {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) throw IllegalStateException(context.getString(R.string.netdisk_err_alist_empty))
        if (trimmed.startsWith("<")) throw IllegalStateException(context.getString(R.string.netdisk_err_alist_html))
        return runCatching { JsonParser.parseString(text).asJsonObject }
            .getOrElse { throw IllegalStateException(context.getString(R.string.netdisk_err_alist_html)) }
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
            .apply {
                val token = config.get("token")?.asString.orEmpty().trim()
                if (token.isNotEmpty()) header("Authorization", token)
            }
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw IllegalStateException(context.getString(R.string.netdisk_err_alist_code, it.code))
            val rootJson = alistJson(it.body?.string().orEmpty())
            when (rootJson.get("code")?.asInt ?: 0) {
                200 -> Unit
                401 -> throw IllegalStateException(context.getString(R.string.netdisk_err_alist_auth))
                else -> throw IllegalStateException(
                    rootJson.get("message")?.asString?.takeIf { message -> message.isNotBlank() }
                        ?: context.getString(R.string.netdisk_err_file_url),
                )
            }
            return rootJson.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
                ?.get("raw_url")?.takeIf { it.isJsonPrimitive }?.asString
                ?: throw IllegalStateException(context.getString(R.string.netdisk_err_raw_url))
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

    /** WebDAV 的 getlastmodified 是 RFC 1123，个别服务端会给 ISO8601，两种都试。 */
    private fun parseHttpDate(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val value = text.trim()
        for (pattern in datePatterns) {
            runCatching {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(value)
                if (parsed != null) return parsed.time
            }
        }
        return 0L
    }

    /** AList / OpenList 的 modified 是 ISO8601（带时区或 Z）。 */
    private fun parseIsoTime(text: String?): Long = parseHttpDate(text)

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
