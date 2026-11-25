package com.onefilm.android.tv.composebridge

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.onefilm.android.tv.aisubtitle.AiSubtitleEngine
import com.onefilm.android.tv.compose.bridge.AiModelBridge
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AI 字幕模型下载实现。
 *
 * <p>清单：`https://onefilmapk.alibabagroup.de5.net/ai-models.json`（见 tools/publish-ai-models.ps1）。
 * 下载先写 `.part`，校验 SHA-256 后再原子重命名；支持 `Range` 断点续传。
 */
class AppAiModelBridge(private val context: Context) : AiModelBridge {

    companion object {
        private const val MANIFEST_URL = "https://onefilmapk.alibabagroup.de5.net/ai-models.json"
        private const val BUFFER = 256 * 1024
        private val client: OkHttpClient by lazy { OkHttpClient() }
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val downloadingState = mutableStateOf(false)
    private val progressState = mutableStateOf(0)

    override val downloading: State<Boolean> get() = downloadingState
    override val progressPct: State<Int> get() = progressState

    override val dataset: String
        get() = AiSubtitleEngine.dataset()

    override var label: String = "普通话"
        private set

    override var totalBytes: Long = 0L
        private set

    override fun isInstalled(): Boolean =
        runCatching { AiSubtitleEngine.modelReady(context) }.getOrDefault(false)

    override fun installedBytes(): Long =
        runCatching { AiSubtitleEngine.modelDir(context).listFiles()?.sumOf { it.length() } ?: 0L }
            .getOrDefault(0L)

    override fun download(
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onDone: (ok: Boolean, message: String?) -> Unit,
    ): AutoCloseable {
        val cancelled = AtomicBoolean(false)
        if (downloadingState.value) return AutoCloseable { cancelled.set(true) }
        downloadingState.value = true
        progressState.value = 0
        Thread {
            try {
                val model = fetchEntry(dataset) ?: throw IllegalStateException("清单中没有该语种")
                label = model.optString("label", label)
                totalBytes = model.optLong("totalBytes", 0L)
                val files = model.getJSONArray("files")
                val dir = AiSubtitleEngine.modelDir(context)
                if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建模型目录")
                // 离线语种（粤语/日语）需要 silero-vad 切段模型（共用目录，只装一次）
                if (AiSubtitleEngine.selectedModel().kind.isOffline()) installVadIfNeeded()
                var done = 0L
                for (i in 0 until files.length()) {
                    if (cancelled.get()) {
                        mainHandler.post { onDone(false, "已取消") }
                        return@Thread
                    }
                    val entry = files.getJSONObject(i)
                    val name = entry.getString("name")
                    val size = entry.getLong("size")
                    val sha = entry.getString("sha256")
                    val target = File(dir, name)
                    val part = File(dir, "$name.part")
                    // 最多 3 次尝试；每次都用 Range 从 .part 现有长度续传
                    var attempt = 0
                    while (true) {
                        attempt++
                        if (part.length() > size) part.delete()
                        if (part.length() < size) {
                            downloadFile(entry.getString("url"), part) { bytes ->
                                val pct = if (totalBytes > 0) ((done + bytes) * 100L / totalBytes).toInt() else 0
                                progressState.value = pct
                                mainHandler.post { onProgress(done + bytes, totalBytes) }
                            }
                        }
                        if (part.length() >= size) break
                        android.util.Log.w("AiSubtitle", "incomplete download $name: ${part.length()}/$size (attempt $attempt)")
                        if (attempt >= 3 || cancelled.get()) {
                            mainHandler.post { onDone(false, "下载不完整（${part.length()}/$size）") }
                            return@Thread
                        }
                    }
                    val actual = sha256(part)
                    if (!actual.equals(sha, ignoreCase = true)) {
                        part.delete()
                        mainHandler.post { onDone(false, "校验失败：$name") }
                        return@Thread
                    }
                    if (target.exists()) target.delete()
                    if (!part.renameTo(target)) {
                        mainHandler.post { onDone(false, "写入失败：$name") }
                        return@Thread
                    }
                    done += size
                    mainHandler.post { onProgress(done, totalBytes) }
                }
                downloadingState.value = false
                progressState.value = 100
                mainHandler.post { onDone(true, null) }
            } catch (t: Throwable) {
                downloadingState.value = false
                mainHandler.post { onDone(false, t.message ?: "下载失败") }
            }
        }.apply { isDaemon = true }.start()
        return AutoCloseable { cancelled.set(true) }
    }

    /** 离线语种需要的 silero-vad 切段模型，装在 ai-models/silero-vad（多个语种共用）。 */
    private fun installVadIfNeeded() {
        val entry = fetchEntry("silero-vad") ?: throw IllegalStateException("清单缺少 silero-vad")
        val dir = File(AiSubtitleEngine.modelDir(context).parentFile, "silero-vad")
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("无法创建 VAD 目录")
        val files = entry.getJSONArray("files")
        for (i in 0 until files.length()) {
            val item = files.getJSONObject(i)
            val name = item.getString("name")
            val size = item.getLong("size")
            val sha = item.getString("sha256")
            val target = File(dir, name)
            if (target.isFile() && target.length() == size && sha256(target).equals(sha, true)) continue
            val part = File(dir, "$name.part")
            var attempt = 0
            while (true) {
                attempt++
                if (part.length() > size) part.delete()
                if (part.length() < size) downloadFile(item.getString("url"), part) { }
                if (part.length() >= size) break
                if (attempt >= 3) throw IllegalStateException("VAD 下载不完整：$name")
            }
            if (!sha256(part).equals(sha, true)) {
                part.delete()
                throw IllegalStateException("VAD 校验失败：$name")
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw IllegalStateException("VAD 写入失败：$name")
        }
    }

    override fun delete() {
        runCatching { AiSubtitleEngine.modelDir(context).deleteRecursively() }
    }

    private fun fetchEntry(dataset: String): JSONObject? {
        val request = Request.Builder().url(MANIFEST_URL).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("清单请求失败：${response.code}")
            val body = response.body?.string() ?: throw IllegalStateException("清单响应为空")
            val models = JSONObject(body).optJSONArray("models") ?: return null
            for (i in 0 until models.length()) {
                val item = models.getJSONObject(i)
                if (item.optString("dataset") == dataset) return item
            }
            return null
        }
    }

    /** 支持 Range 续传；回调的是「本次新增」的字节数。 */
    private fun downloadFile(url: String, part: File, onBytes: (Long) -> Unit) {
        val existing = if (part.exists()) part.length() else 0L
        val builder = Request.Builder().url(url)
        if (existing > 0L) builder.header("Range", "bytes=$existing-")
        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("下载失败：${response.code}")
            val body = response.body ?: throw IllegalStateException("下载响应为空")
            FileOutputStream(part, existing > 0L).use { output ->
                val buffer = ByteArray(BUFFER)
                var added = 0L
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        added += read
                        onBytes(added)
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
