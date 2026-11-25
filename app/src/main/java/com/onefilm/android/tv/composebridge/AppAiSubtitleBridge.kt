package com.onefilm.android.tv.composebridge

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.onefilm.android.tv.aisubtitle.AiSubtitleEngine
import com.onefilm.android.tv.compose.bridge.AiSubtitleBridge
import com.onefilm.android.tv.compose.bridge.AiSubtitleCue
import com.onefilm.android.tv.compose.bridge.AiTranslationConfig
import com.onefilm.android.tv.player.exo.AiAudioClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AI 字幕桥接实现（TV / 手机共用，位于共享源集）。
 *
 * <p>LITE 侧由 `appcore-lite/src/shim/.../aisubtitle/` 的功能桩提供同包名类，
 * `available` 恒为 false，设置页与播放器菜单因此自动隐藏入口。
 */
class AppAiSubtitleBridge(private val context: Context) : AiSubtitleBridge {

    private val cueSinks = CopyOnWriteArrayList<(AiSubtitleCue) -> Unit>()

    /**
     * 语言默认值：TV（leanback）默认精简模型，手机默认标准模型。
     *
     * <p>4 核 Amlogic 电视跑 162MB 大模型 RTF≈2.3，字幕会严重滞后；
     * 26MB 精简模型才能跟上实时。用户可在设置里随时改。
     */
    private val defaultLanguage: String by lazy {
        val leanback = runCatching {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        }.getOrDefault(false)
        if (leanback) "zh_small" else "zh"
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Compose 可观察的开关状态：播放中切换会触发 UI 重新订阅。 */
    private val enabledStateImpl = mutableStateOf(
        com.github.catvod.utils.Prefers.getBoolean("ai_subtitle_enabled", false),
    )

    init {
        AiSubtitleEngine.get().setAppContext(context)
        // 识别语种：引擎不依赖 Prefers，由宿主注入；默认普通话
        AiSubtitleEngine.setLanguage(
            com.github.catvod.utils.Prefers.getString("ai_subtitle_language", defaultLanguage)
        )
        // 开关实时生效：抽头常驻，引擎在 feedPcm 里按开关懒启停（无需重播）
        AiSubtitleEngine.get().setEnabledProvider {
            com.github.catvod.utils.Prefers.getBoolean("ai_subtitle_enabled", false)
        }
        AiSubtitleEngine.get().setPositionProvider { AiAudioClock.playedMs() }
        // 排程以配置的预读量为准（实测值受 AudioSink 缓冲层级影响，会稳定偏离约 -1.2s）
        AiSubtitleEngine.get().setConfiguredLookaheadMs(
            com.onefilm.android.tv.player.exo.AiAudioTrackBufferSizeProvider.LOOKAHEAD_MS.toLong()
        )

        AiSubtitleEngine.get().setCueListener { text, startUs, endUs, lateUs, lookaheadMs ->
            deliverCue(text, startUs, endUs, lateUs, lookaheadMs)
        }
    }

    /**
     * 投递字幕：开了翻译就先等译文，再一次性上屏（原子双语，避免先出原文再被替换）。
     *
     * <p>翻译失败时回退为识别原文（上游选择整句丢弃，这里改成为保住可读性——
     * 判定仍发生在投递前，不会出现「先原文后译文」的跳动）。
     */
    private fun deliverCue(text: String, startUs: Long, endUs: Long, lateUs: Long, lookaheadMs: Long) {
        val createdAt = SystemClock.elapsedRealtime()
        if (!AppAiTranslator.shouldTranslate()) {
            emit(AiSubtitleCue(text, startUs, endUs, lateUs, lookaheadMs, createdAt))
            return
        }
        val mode = AppAiTranslator.mode()
        AppAiTranslator.translate(text) { translated ->
            // 翻译太久（超过 8s）说明这句早已说完，宁可丢弃也不补一条严重滞后的字幕
            val age = SystemClock.elapsedRealtime() - createdAt
            if (age > 8_000L) {
                Log.w("AiSubtitle", "translation too late (${age}ms), cue dropped: ${text.take(16)}")
                return@translate
            }
            val display = AppAiTranslator.composeCue(mode, text, translated)
            emit(AiSubtitleCue(display, startUs, endUs, lateUs, lookaheadMs, createdAt))
        }
    }

    private fun emit(cue: AiSubtitleCue) {
        for (sink in cueSinks) {
            runCatching { sink(cue) }
        }
    }

    override val available: Boolean
        get() = AiSubtitleEngine.modelReady(context)

    /** 开关来自设置页（默认关）；模型未安装时永远视为关闭。 */
    override fun isEnabled(): Boolean = available && enabledStateImpl.value

    override val enabledState: State<Boolean>
        get() = enabledStateImpl

    override fun setEnabled(enabled: Boolean) {
        com.github.catvod.utils.Prefers.put("ai_subtitle_enabled", enabled)
        enabledStateImpl.value = enabled
        // 关闭时立刻释放模型与识别线程；抽头保留，下次开启无需重播
        if (!enabled) {
            runCatching { AppAiTranslator.cancelAll() }
            runCatching { AiSubtitleEngine.get().shutdown() }
        }
    }

    override fun openSettings() {
        // P3：跳转到「AI 字幕」设置子页
    }

    override fun observeCues(sink: (AiSubtitleCue) -> Unit): AutoCloseable {
        cueSinks.add(sink)
        return AutoCloseable {
            cueSinks.remove(sink)
            // 播放器销毁后不再需要任何在飞的翻译
            if (cueSinks.isEmpty()) runCatching { AppAiTranslator.cancelAll() }
        }
    }

    // ---- 显示样式（P6）----

    private val styleStateImpl = mutableStateOf(readStyle())

    private fun readStyle(): com.onefilm.android.tv.compose.bridge.AiSubtitleStyle =
        com.onefilm.android.tv.compose.bridge.AiSubtitleStyle(
            offsetDp = com.github.catvod.utils.Prefers.getInt("ai_subtitle_offset", 0),
            color = com.github.catvod.utils.Prefers.getInt("ai_subtitle_color", 0),
            sizeScale = com.github.catvod.utils.Prefers.getFloat("ai_subtitle_size", 1f),
            bold = com.github.catvod.utils.Prefers.getBoolean("ai_subtitle_bold", false),
            stroke = com.github.catvod.utils.Prefers.getInt("ai_subtitle_stroke", 0),
            font = com.github.catvod.utils.Prefers.getInt("ai_subtitle_font", 0),
        )

    override fun subtitleStyle(): com.onefilm.android.tv.compose.bridge.AiSubtitleStyle = styleStateImpl.value

    override val subtitleStyleState: State<com.onefilm.android.tv.compose.bridge.AiSubtitleStyle>
        get() = styleStateImpl

    override fun setSubtitleStyle(style: com.onefilm.android.tv.compose.bridge.AiSubtitleStyle) {
        com.github.catvod.utils.Prefers.put("ai_subtitle_offset", style.offsetDp)
        com.github.catvod.utils.Prefers.put("ai_subtitle_color", style.color)
        com.github.catvod.utils.Prefers.put("ai_subtitle_size", style.sizeScale)
        com.github.catvod.utils.Prefers.put("ai_subtitle_bold", style.bold)
        com.github.catvod.utils.Prefers.put("ai_subtitle_stroke", style.stroke)
        com.github.catvod.utils.Prefers.put("ai_subtitle_font", style.font)
        styleStateImpl.value = style
    }
    // ---- 识别语种（P6）----

    override fun languageCode(): String =
        com.github.catvod.utils.Prefers.getString("ai_subtitle_language", defaultLanguage)

    override fun languageOptions(): List<com.onefilm.android.tv.compose.bridge.AiLanguageOption> =
        runCatching {
            com.onefilm.android.tv.aisubtitle.AsrCatalog.all()
                .map { com.onefilm.android.tv.compose.bridge.AiLanguageOption(it.code, it.label) }
        }.getOrDefault(emptyList())

    override fun setLanguage(code: String) {
        com.github.catvod.utils.Prefers.put("ai_subtitle_language", code)
        AiSubtitleEngine.setLanguage(code)
        // 换语种要换模型：立即释放，下次开启按新语种加载
        runCatching { AiSubtitleEngine.get().shutdown() }
    }

    // ---- 翻译配置（P5）----

    override fun translationConfig(): AiTranslationConfig = AppAiTranslator.config()

    override fun setTranslationEnabled(enabled: Boolean) {
        AppAiTranslator.setEnabled(enabled)
        if (!enabled) runCatching { AppAiTranslator.cancelAll() }
    }

    override fun setTranslationProvider(provider: Int) {
        AppAiTranslator.setProvider(provider)
        runCatching { AppAiTranslator.cancelAll() }
    }

    override fun setSubtitleMode(mode: Int) = AppAiTranslator.setMode(mode)

    override fun setMTranUrl(url: String) = AppAiTranslator.setMTranUrl(url)

    override fun setMTranToken(token: String) = AppAiTranslator.setMTranToken(token)

    override fun setMTranName(name: String) = AppAiTranslator.setMTranName(name)

    override fun setApiBaseUrl(url: String) = AppAiTranslator.setBaseUrl(url)

    override fun setApiKey(key: String) = AppAiTranslator.setApiKey(key)

    override fun setApiModel(model: String) = AppAiTranslator.setTranslationModel(model)

    override fun setApiName(name: String) = AppAiTranslator.setLLMName(name)

    override fun setThinkingEnabled(enabled: Boolean) = AppAiTranslator.setThinkingEnabled(enabled)

    override fun setContextEnabled(enabled: Boolean) = AppAiTranslator.setContextEnabled(enabled)

    override fun testTranslation(onResult: (ok: Boolean, detail: String) -> Unit) {
        AppAiTranslator.test { ok, detail -> mainHandler.post { onResult(ok, detail) } }
    }

    override fun testTranslationProvider(
        provider: Int,
        onResult: (ok: Boolean, detail: String) -> Unit,
    ) {
        AppAiTranslator.test(provider) { ok, detail -> mainHandler.post { onResult(ok, detail) } }
    }
}
