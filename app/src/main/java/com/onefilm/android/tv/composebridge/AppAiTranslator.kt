package com.onefilm.android.tv.composebridge

import android.util.Base64
import android.util.Log
import com.github.catvod.utils.Prefers
import com.onefilm.android.tv.compose.bridge.AiTranslationConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AI 字幕翻译器（三条通道，与上游一致）。
 *
 * <ul>
 *   <li><b>DEFAULT</b>：有道网页版公开实验端点，无需账号/Key；</li>
 *   <li><b>MTRAN</b>：用户自填 MTranServer 地址（本地或局域网部署）；</li>
 *   <li><b>OPENAI</b>：OpenAI 兼容大模型（baseUrl + apiKey + model）。</li>
 * </ul>
 *
 * <p>串行队列：同一时刻只有一条翻译在飞，至多 3 条排队，积压时丢弃最旧任务，
 * 保证字幕按语序上屏。翻译失败返回 null，调用方回退为只显示识别原文
 * （与上游「失败即丢弃整句」不同：宁可显示原文，也不留整句空白）。
 */
object AppAiTranslator {

    private const val TAG = "AiSubtitle"

    // ---- 设置键（与设置页共用）----
    const val KEY_ENABLED = "ai_subtitle_translate"
    const val KEY_PROVIDER = "ai_subtitle_translation_provider"
    const val KEY_MODE = "ai_subtitle_mode"
    const val KEY_MTRAN_URL = "ai_subtitle_mtran_url"
    const val KEY_MTRAN_TOKEN = "ai_subtitle_mtran_token"
    const val KEY_MTRAN_NAME = "ai_subtitle_mtran_name"
    const val KEY_LLM_NAME = "ai_subtitle_llm_name"
    const val KEY_BASE_URL = "ai_subtitle_base_url"
    const val KEY_API_KEY = "ai_subtitle_api_key"
    const val KEY_MODEL = "ai_subtitle_model"
    const val KEY_THINKING = "ai_subtitle_thinking"
    const val KEY_CONTEXT = "ai_subtitle_context"

    // ---- 枚举（用 Int 存 Prefers，便于设置页直接绑定）----
    const val PROVIDER_DEFAULT = 0
    const val PROVIDER_MTRAN = 1
    const val PROVIDER_OPENAI = 2
    const val PROVIDER_TENCENT = 3
    const val PROVIDER_BING = 4
    const val MODE_BILINGUAL = 0
    const val MODE_TRANSLATED_ONLY = 1

    private const val TARGET = "zh-CHS"
    private const val MAX_PENDING = 3
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    private const val OPENAI_SYSTEM_PROMPT =
        "你是实时字幕翻译器。把输入的字幕翻译成自然、简洁的简体中文。只输出译文，不解释，不添加引号。"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    init {
        // 旧版本把 Key/Token 明文存在 Prefers，升级后迁移到加密存储并清掉明文
        runCatching {
            val legacyKey = Prefers.getString(KEY_API_KEY, "")
            if (legacyKey.isNotBlank()) {
                AiSecretStore.putApiKey(legacyKey)
                Prefers.remove(KEY_API_KEY)
            }
            val legacyToken = Prefers.getString(KEY_MTRAN_TOKEN, "")
            if (legacyToken.isNotBlank()) {
                AiSecretStore.putMTranToken(legacyToken)
                Prefers.remove(KEY_MTRAN_TOKEN)
            }
        }
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ---- 有道密钥缓存 ----
    private data class YoudaoKeys(val secret: String, val aesKey: String, val aesIv: String, val createdAt: Long)

    @Volatile private var youdaoKeys: YoudaoKeys? = null
    @Volatile private var activeCall: Call? = null

    // ---- 串行队列 ----
    private class Task(val source: String, val provider: Int, val epoch: Long, val callback: (String?) -> Unit)

    private val lock = Any()
    private var active: Task? = null
    private val pending = ArrayDeque<Task>()
    @Volatile private var epoch = 0L

    // ---- 上下文（仅大模型，默认关）----
    private data class History(val source: String, val translated: String)
    private val history = ArrayDeque<History>()

    // ---------------------------------------------------------------------
    // 设置读写
    // ---------------------------------------------------------------------

    fun config(): AiTranslationConfig = AiTranslationConfig(
        enabled = enabled(),
        provider = provider(),
        mode = mode(),
        mtranName = mtranName(),
        mtranUrl = mtranUrl(),
        mtranToken = mtranToken(),
        llmName = llmName(),
        baseUrl = baseUrl(),
        apiKey = apiKey(),
        model = translationModel(),
        thinking = thinkingEnabled(),
        context = contextEnabled(),
    )

    fun enabled(): Boolean = Prefers.getBoolean(KEY_ENABLED, false)
    fun setEnabled(value: Boolean) = Prefers.put(KEY_ENABLED, value)

    fun provider(): Int = Prefers.getInt(KEY_PROVIDER, PROVIDER_DEFAULT).coerceIn(0, 4)
    fun setProvider(value: Int) = Prefers.put(KEY_PROVIDER, value.coerceIn(0, 4))

    fun mode(): Int = Prefers.getInt(KEY_MODE, MODE_BILINGUAL).coerceIn(0, 1)
    fun setMode(value: Int) = Prefers.put(KEY_MODE, value.coerceIn(0, 1))

    fun mtranUrl(): String = Prefers.getString(KEY_MTRAN_URL, "http://localhost:8989").trim()
    fun setMTranUrl(value: String) = Prefers.put(KEY_MTRAN_URL, value.trim())

    fun mtranName(): String = Prefers.getString(KEY_MTRAN_NAME, "").trim()
    fun setMTranName(value: String) = Prefers.put(KEY_MTRAN_NAME, value.trim())

    fun llmName(): String = Prefers.getString(KEY_LLM_NAME, "").trim()
    fun setLLMName(value: String) = Prefers.put(KEY_LLM_NAME, value.trim())

    fun mtranToken(): String = Prefers.getString(KEY_MTRAN_TOKEN, "").trim()
    fun setMTranToken(value: String) = Prefers.put(KEY_MTRAN_TOKEN, value.trim())

    fun baseUrl(): String = Prefers.getString(KEY_BASE_URL, "https://api.deepseek.com").trim()
    fun setBaseUrl(value: String) = Prefers.put(KEY_BASE_URL, value.trim())

    fun apiKey(): String = Prefers.getString(KEY_API_KEY, "").trim()
    fun setApiKey(value: String) = Prefers.put(KEY_API_KEY, value.trim())

    fun translationModel(): String = Prefers.getString(KEY_MODEL, "deepseek-chat").trim()
    fun setTranslationModel(value: String) = Prefers.put(KEY_MODEL, value.trim())

    fun thinkingEnabled(): Boolean = Prefers.getBoolean(KEY_THINKING, false)
    fun setThinkingEnabled(value: Boolean) = Prefers.put(KEY_THINKING, value)

    fun contextEnabled(): Boolean = Prefers.getBoolean(KEY_CONTEXT, false)
    fun setContextEnabled(value: Boolean) = Prefers.put(KEY_CONTEXT, value)

    /** 当前通道是否配置完整（设置页据此给出提示）。 */
    fun configured(): Boolean = when (provider()) {
        PROVIDER_MTRAN -> mtranUrl().isNotBlank()
        PROVIDER_OPENAI -> baseUrl().isNotBlank() && apiKey().isNotBlank() && translationModel().isNotBlank()
        else -> true
    }

    // ---------------------------------------------------------------------
    // 字幕合成（与上游 composeCue 一致）
    // ---------------------------------------------------------------------

    /**
     * 组合最终上屏文本。
     *
     * <p>原文与译文相同、或源语言本身就是中文、或用户选「仅译文」时只显示译文；
     * 否则显示「译文 + 换行 + 原文」。
     */
    fun composeCue(mode: Int, source: String, translated: String?): String {
        val original = source.trim()
        val result = translated?.trim().orEmpty()
        if (result.isEmpty()) return original
        if (sourceIsChinese() || mode == MODE_TRANSLATED_ONLY || original == result) return result
        return result + "\n" + original
    }

    /** 是否应当翻译：开关打开、通道配置完整、且源语言不是中文。 */
    fun shouldTranslate(): Boolean = enabled() && configured() && !sourceIsChinese()

    /**
     * 源语言是否为中文。
     *
     * <p>由 ASR 语种决定（`ai_subtitle_language`，默认 zh）。中文识别结果再翻译成中文没有意义，
     * 反而给每条字幕加 200~800ms 延迟，因此直接跳过。P6 装上外语模型后自动生效。
     */
    private fun sourceIsChinese(): Boolean =
        Prefers.getString("ai_subtitle_language", "zh").trim().lowercase(Locale.ROOT)
            .let { it == "zh" || it == "zh_small" || it == "cmn" || it == "mandarin" || it == "yue" }

    // ---------------------------------------------------------------------
    // 串行翻译入口
    // ---------------------------------------------------------------------

    /**
     * 翻译一句字幕。回调在 OkHttp 线程；失败回调 null。
     */
    fun translate(source: String, callback: (String?) -> Unit) {
        val text = source.trim()
        if (text.isEmpty()) {
            callback(null)
            return
        }
        val task = Task(text, provider(), epoch, callback)
        var start: Task? = null
        synchronized(lock) {
            if (active == null) {
                active = task
                start = task
            } else {
                if (pending.size >= MAX_PENDING) {
                    val dropped = pending.removeFirst()
                    Log.i(TAG, "translation backlog dropped: ${dropped.source.take(16)}")
                }
                pending.addLast(task)
            }
        }
        if (start != null) dispatch(start)
    }

    /** 取消所有排队与进行中的翻译，并作废旧回调（换片/关字幕时调用）。 */
    fun cancelAll() {
        epoch++
        synchronized(lock) {
            active = null
            pending.clear()
        }
        activeCall?.cancel()
        activeCall = null
        synchronized(history) { history.clear() }
    }

    private fun dispatch(task: Task) {
        val done: (String?) -> Unit = { translated -> finish(task, translated) }
        when (task.provider) {
            PROVIDER_MTRAN -> translateMTran(task.source, done)
            PROVIDER_OPENAI -> translateOpenAi(task.source, done)
            PROVIDER_TENCENT -> translateTencent(task.source, done)
            PROVIDER_BING -> translateBing(task.source, done)
            else -> translateYoudao(task.source, done)
        }
    }

    private fun finish(task: Task, translated: String?) {
        var next: Task? = null
        synchronized(lock) {
            if (active === task) {
                active = null
                while (pending.isNotEmpty()) {
                    val candidate = pending.removeFirst()
                    if (candidate.epoch == epoch) {
                        active = candidate
                        next = candidate
                        break
                    }
                }
            }
        }
        // 过期回调（换片/关闭）直接丢弃
        if (task.epoch == epoch) runCatching { task.callback(translated) }
        val toStart = next
        if (toStart != null) dispatch(toStart)
    }

    // ---------------------------------------------------------------------
    // 通道 1：有道公开端点
    // ---------------------------------------------------------------------

    private fun translateYoudao(source: String, done: (String?) -> Unit) {
        val cached = youdaoKeys
        if (cached != null && System.currentTimeMillis() - cached.createdAt < 600_000L) {
            youdaoTranslate(cached, source, done)
        } else {
            youdaoKeys(source, done)
        }
    }

    private fun youdaoKeys(source: String, done: (String?) -> Unit) {
        val time = System.currentTimeMillis().toString()
        val sign = md5Hex("client=fanyideskweb&mysticTime=$time&product=webfanyi&key=asdjnjfenknafdfsdfsd")
        val url = "https://dict.youdao.com/webtranslate/key?client=fanyideskweb&product=webfanyi" +
            "&appVersion=1.0.0&vendor=web&pointParam=client%2CmysticTime%2Cproduct&keyfrom=fanyi.web" +
            "&keyid=webfanyi-key-getter&sign=$sign&mysticTime=$time"
        val call = client.newCall(youdaoBase(Request.Builder().url(url)).get().build())
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "youdao keys failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val keys = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val body = response.body?.string() ?: error("empty")
                        val data = JSONObject(body).optJSONObject("data") ?: error("no data")
                        YoudaoKeys(
                            data.getString("secretKey"),
                            data.getString("aesKey"),
                            data.getString("aesIv"),
                            System.currentTimeMillis(),
                        )
                    }.getOrElse { e ->
                        Log.w(TAG, "youdao keys parse failed: ${e.message}")
                        null
                    }
                    if (keys == null) {
                        done(null)
                    } else {
                        youdaoKeys = keys
                        youdaoTranslate(keys, source, done)
                    }
                }
            }
        })
    }

    private fun youdaoTranslate(keys: YoudaoKeys, source: String, done: (String?) -> Unit) {
        val time = System.currentTimeMillis().toString()
        val sign = md5Hex("client=fanyideskweb&mysticTime=$time&product=webfanyi&key=${keys.secret}")
        val body = FormBody.Builder()
            .add("client", "fanyideskweb").add("product", "webfanyi").add("appVersion", "1.0.0")
            .add("vendor", "web").add("pointParam", "client,mysticTime,product").add("keyfrom", "fanyi.web")
            .add("keyid", "webfanyi").add("sign", sign).add("mysticTime", time).add("i", source)
            .add("from", "auto").add("to", TARGET).add("dictResult", "false").build()
        val call = client.newCall(
            youdaoBase(Request.Builder().url("https://dict.youdao.com/webtranslate")).post(body).build()
        )
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "youdao translate failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val encoded = response.body?.string() ?: error("empty")
                        val plain = decryptYoudao(encoded, keys.aesKey, keys.aesIv)
                        val paragraphs = JSONObject(plain).optJSONArray("translateResult")
                            ?: error("no translateResult")
                        val sb = StringBuilder()
                        for (i in 0 until paragraphs.length()) {
                            val parts = paragraphs.getJSONArray(i)
                            if (i > 0) sb.append('\n')
                            for (j in 0 until parts.length()) {
                                sb.append(parts.getJSONObject(j).optString("tgt", ""))
                            }
                        }
                        sb.toString().trim().ifBlank { null }
                    }.getOrElse { e ->
                        Log.w(TAG, "youdao translate parse failed: ${e.message}")
                        null
                    }
                    done(result)
                }
            }
        })
    }

    private fun youdaoBase(builder: Request.Builder): Request.Builder = builder
        .header("User-Agent", USER_AGENT)
        .header("Referer", "https://fanyi.youdao.com")
        .header("Cookie", "OUTFOX_SEARCH_USER_ID=1796239350@10.110.96.157;")

    private fun decryptYoudao(encoded: String, key: String, iv: String): String {
        val data = Base64.decode(encoded.replace('-', '+').replace('_', '/'), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(md5(key), "AES"), IvParameterSpec(md5(iv)))
        return String(cipher.doFinal(data), StandardCharsets.UTF_8)
    }

    // ---------------------------------------------------------------------
    // 通道 2：MTranServer
    // ---------------------------------------------------------------------

    private fun translateMTran(source: String, done: (String?) -> Unit) {
        val body = runCatching {
            JSONObject()
                .put("from", "auto")
                .put("to", "zh-Hans")
                .put("text", source)
                .put("html", false)
                .toString()
        }.getOrElse {
            done(null)
            return
        }
        val builder = Request.Builder()
            .url(mtranEndpoint(mtranUrl()))
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
        val token = mtranToken()
        if (token.isNotEmpty()) builder.header("Authorization", "Bearer $token")
        val call = client.newCall(builder.build())
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "mtran failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val text = response.body?.string() ?: error("empty")
                        JSONObject(text).optString("result", "").trim().ifBlank { null }
                    }.getOrElse { e ->
                        Log.w(TAG, "mtran parse failed: ${e.message}")
                        null
                    }
                    done(result)
                }
            }
        })
    }

    // ---------------------------------------------------------------------
    // 通道 3：OpenAI 兼容大模型
    // ---------------------------------------------------------------------

    private fun translateOpenAi(source: String, done: (String?) -> Unit) {
        val key = apiKey()
        if (key.isEmpty()) {
            Log.w(TAG, "openai translation: api key missing")
            done(null)
            return
        }
        val payload = runCatching { buildOpenAiJson(source).toString() }.getOrElse {
            done(null)
            return
        }
        val call = client.newCall(
            Request.Builder()
                .url(openAiEndpoint(baseUrl()))
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .post(payload.toRequestBody(JSON))
                .build()
        )
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "openai failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val text = response.body?.string() ?: error("empty")
                        val choices = JSONObject(text).optJSONArray("choices") ?: error("no choices")
                        if (choices.length() == 0) error("empty choices")
                        choices.getJSONObject(0).getJSONObject("message")
                            .optString("content", "").trim().ifBlank { null }
                    }.getOrElse { e ->
                        Log.w(TAG, "openai parse failed: ${e.message}")
                        null
                    }
                    if (result != null && contextEnabled()) {
                        synchronized(history) {
                            while (history.size >= 2) history.removeFirst()
                            history.addLast(History(source, result))
                        }
                    }
                    done(result)
                }
            }
        })
    }

    private fun buildOpenAiJson(source: String): JSONObject {
        val root = JSONObject()
        root.put("model", translationModel())
        root.put("stream", false)
        root.put("max_tokens", 192)
        root.put("temperature", 0)
        // DeepSeek 明确支持 thinking 扩展；其它 OpenAI 兼容服务常拒绝多余字段，因此按域名判断
        val model = translationModel().lowercase(Locale.ROOT)
        val base = baseUrl().lowercase(Locale.ROOT)
        if (model.contains("deepseek") || base.contains("deepseek")) {
            root.put("thinking", JSONObject().put("type", if (thinkingEnabled()) "enabled" else "disabled"))
        }
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", OPENAI_SYSTEM_PROMPT))
        if (contextEnabled()) {
            synchronized(history) {
                for (item in history) {
                    messages.put(JSONObject().put("role", "user").put("content", item.source))
                    messages.put(JSONObject().put("role", "assistant").put("content", item.translated))
                }
            }
        } else {
            synchronized(history) { history.clear() }
        }
        messages.put(JSONObject().put("role", "user").put("content", source))
        root.put("messages", messages)
        return root
    }

    // ---------------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------------

    // ---------------------------------------------------------------------
    // 通道 4：腾讯翻译君（免部署，网页端接口，移植自发布版 h32/na3）
    // ---------------------------------------------------------------------

    private fun translateTencent(source: String, done: (String?) -> Unit) {
        val payload = runCatching {
            JSONObject()
                .put(
                    "header",
                    JSONObject()
                        .put("fn", "auto_translation")
                        .put("session", "")
                        .put("client_key", "browser-android-java-${UUID.randomUUID()}-${System.currentTimeMillis()}")
                        .put("user", ""),
                )
                .put("type", "plain")
                .put("model_category", "normal")
                .put("text_domain", "")
                .put("source", JSONObject().put("lang", tencentSourceLang()).put("text_list", JSONArray().put(source)))
                .put("target", JSONObject().put("lang", "zh"))
                .toString()
        }.getOrElse {
            done(null)
            return
        }
        val call = client.newCall(
            Request.Builder()
                .url("https://transmart.qq.com/api/imt")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Content-Type", "application/json")
                .header("Origin", "https://transmart.qq.com")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", "https://transmart.qq.com/zh-CN/index")
                .post(payload.toRequestBody(JSON))
                .build()
        )
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "tencent failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val root = JSONObject(response.body?.string() ?: error("empty"))
                        val code = root.optJSONObject("header")?.optString("ret_code", "").orEmpty()
                        if (code != "succ") error("ret_code=$code")
                        val arr = root.optJSONArray("auto_translation") ?: error("no auto_translation")
                        val sb = StringBuilder()
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i, "").trim()
                            if (item.isNotEmpty()) {
                                if (sb.isNotEmpty()) sb.append('\n')
                                sb.append(item)
                            }
                        }
                        sb.toString().ifBlank { null }
                    }.getOrElse { e ->
                        Log.w(TAG, "tencent parse failed: ${e.message}")
                        null
                    }
                    done(result)
                }
            }
        })
    }

    /** 腾讯的语种代码：粤语是 ct。 */
    private fun tencentSourceLang(): String = when (val code = sourceLanguage()) {
        "yue" -> "ct"
        "zh" -> "zh"
        else -> code
    }

    // ---------------------------------------------------------------------
    // 通道 5：Bing 翻译（免部署，移植自发布版 rk/qk）
    // 先 GET cn.bing.com/translator 解析 params_AbusePreventionHelper(IG/token/IID)，
    // 再 POST /ttranslatev3。会话缓存 8 分钟，失败自动重新引导一次。
    // ---------------------------------------------------------------------

    private data class BingSession(
        val key: String,
        val token: String,
        val ig: String,
        val iid: String,
        val path: String,
        val createdAt: Long,
    )

    @Volatile
    private var bingSession: BingSession? = null

    private fun translateBing(source: String, done: (String?) -> Unit) {
        val session = bingSession
        if (session != null && System.currentTimeMillis() - session.createdAt < 8 * 60_000L) {
            bingTranslate(session, source, done, retry = true)
        } else {
            bingBootstrap(source, done)
        }
    }

    private fun bingBootstrap(source: String, done: (String?) -> Unit) {
        val call = client.newCall(
            Request.Builder()
                .url("https://cn.bing.com/translator?mkt=zh-CN")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build()
        )
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "bing bootstrap failed: ${e.javaClass.simpleName}")
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val html = runCatching { response.body?.string().orEmpty() }.getOrDefault("")
                    val parsed = runCatching { parseBingSession(html) }.getOrNull()
                    if (parsed == null) {
                        Log.w(TAG, "bing bootstrap parse failed")
                        done(null)
                    } else {
                        bingSession = parsed
                        bingTranslate(parsed, source, done, retry = true)
                    }
                }
            }
        })
    }

    private fun parseBingSession(html: String): BingSession? {
        val helper = Regex("""params_AbusePreventionHelper\s*=\s*\[\s*(\d+)\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*]""")
            .find(html) ?: return null
        val ig = Regex("""\bIG\s*:\s*"([A-Fa-f0-9]{16,})"""").find(html)?.groupValues?.get(1)
            ?: Regex("""\bIG\s*:\s*"([A-Fa-f0-9]+)"""").find(html)?.groupValues?.get(1)
            ?: return null
        val iid = Regex("""id="rich_tta"[^>]{0,300}?data-iid="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: return null
        val path = Regex("""var\s+params_RichTranslate\s*=\s*\[\s*"([^"]*ttranslatev3[^"]*)"""").find(html)?.groupValues?.get(1)
            ?: "/ttranslatev3?isVertical=1&"
        return BingSession(helper.groupValues[1], helper.groupValues[2], ig, iid, path, System.currentTimeMillis())
    }

    private fun bingTranslate(session: BingSession, source: String, done: (String?) -> Unit, retry: Boolean) {
        val path = session.path.substringBefore('?').ifBlank { "/ttranslatev3" }
        val url = runCatching {
            "https://cn.bing.com$path".toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("isVertical", "1")
                ?.addQueryParameter("IG", session.ig)
                ?.addQueryParameter("IID", session.iid)
                ?.addQueryParameter("SFX", "1")
                ?.build()
        }.getOrNull()
        if (url == null) {
            done(null)
            return
        }
        val form = FormBody.Builder()
            .add("fromLang", bingSourceLang())
            .add("to", "zh-Hans")
            .add("tone", "Casual")
            .add("text", source)
            .add("token", session.token)
            .add("key", session.key)
            .build()
        val call = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", "https://cn.bing.com")
                .header("Referer", "https://cn.bing.com/translator?mkt=zh-CN")
                .post(form)
                .build()
        )
        activeCall = call
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                if (call.isCanceled()) return
                Log.w(TAG, "bing failed: ${e.javaClass.simpleName}")
                bingSession = null
                done(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val text = runCatching {
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val body = response.body?.string() ?: error("empty")
                        val parsed = JSONTokener(body).nextValue()
                        val obj = when (parsed) {
                            is JSONArray -> parsed.optJSONObject(0)
                            is JSONObject -> parsed
                            else -> null
                        } ?: error("bad body")
                        obj.optJSONArray("translations")?.optJSONObject(0)?.optString("text", "")?.trim()
                    }.getOrElse { e ->
                        Log.w(TAG, "bing parse failed: ${e.message}")
                        null
                    }
                    if (text.isNullOrBlank() && retry) {
                        bingSession = null
                        bingBootstrap(source, done)
                    } else {
                        done(text?.ifBlank { null })
                    }
                }
            }
        })
    }

    /** Bing 的语种代码（简体中文用 zh-Hans）。 */
    private fun bingSourceLang(): String = when (val code = sourceLanguage()) {
        "zh" -> "zh-Hans"
        "yue" -> "yue"
        else -> code
    }

    /** 当前 ASR 语种代码（zh/en/de/fr/es/ko/ru/yue/ja）。 */
    private fun sourceLanguage(): String =
        Prefers.getString("ai_subtitle_language", "zh").trim().lowercase(Locale.ROOT).ifBlank { "zh" }
    private fun openAiEndpoint(baseUrl: String): String {
        val base = normalizeBase(baseUrl)
        return when {
            base.isEmpty() -> "https://api.deepseek.com/chat/completions"
            base.endsWith("/chat/completions") -> base
            else -> "$base/chat/completions"
        }
    }

    private fun mtranEndpoint(baseUrl: String): String {
        val base = normalizeBase(baseUrl)
        return when {
            base.isEmpty() -> "http://localhost:8989/translate"
            base.endsWith("/translate") -> base
            else -> "$base/translate"
        }
    }

    private fun normalizeBase(value: String): String {
        var base = value.trim()
        while (base.endsWith("/")) base = base.substring(0, base.length - 1)
        return base
    }

    private fun md5Hex(value: String): String {
        val sb = StringBuilder(32)
        for (b in md5(value)) sb.append("%02x".format(b.toInt() and 0xff))
        return sb.toString()
    }

    private fun md5(value: String): ByteArray =
        MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.UTF_8))

    /** 用当前通道翻译一句固定文本，用于设置页「测试」（独立直连，不占播放队列）。 */
    fun test(onResult: (ok: Boolean, detail: String) -> Unit) = test(provider(), onResult)

    /** 用指定通道翻译一句固定文本（不改动当前选中通道）。 */
    fun test(target: Int, onResult: (ok: Boolean, detail: String) -> Unit) {
        // 先做配置完整性检查，失败原因要能指导用户去改哪个字段
        when (target) {
            PROVIDER_MTRAN -> if (mtranUrl().isBlank()) {
                onResult(false, "未配置 MTranServer 地址")
                return
            }

            PROVIDER_OPENAI -> {
                if (baseUrl().isBlank()) {
                    onResult(false, "未配置接口地址")
                    return
                }
                if (apiKey().isBlank()) {
                    onResult(false, "未配置 API Key")
                    return
                }
                if (translationModel().isBlank()) {
                    onResult(false, "未配置模型名")
                    return
                }
            }
        }
        val sample = "Hello, how are you today?"
        val started = android.os.SystemClock.elapsedRealtime()
        val done: (String?) -> Unit = { translated ->
            val ms = android.os.SystemClock.elapsedRealtime() - started
            if (translated.isNullOrBlank()) {
                onResult(false, "连接失败（${ms}ms）")
            } else {
                onResult(true, "$translated（${ms}ms）")
            }
        }
        when (target) {
            PROVIDER_MTRAN -> translateMTran(sample, done)
            PROVIDER_OPENAI -> translateOpenAi(sample, done)
            PROVIDER_TENCENT -> translateTencent(sample, done)
            PROVIDER_BING -> translateBing(sample, done)
            else -> translateYoudao(sample, done)
        }
    }
}
