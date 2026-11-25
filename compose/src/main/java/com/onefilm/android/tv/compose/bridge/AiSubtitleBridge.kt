package com.onefilm.android.tv.compose.bridge

import androidx.compose.runtime.State

/** 一条 AI 字幕：识别文本 + 对应语音在音频时间轴上的区间 + 排程所需的延迟信息。 */
data class AiSubtitleCue(
    val text: String,
    /** 语音起点（微秒，音频时间轴）。 */
    val startUs: Long,
    /** 语音终点（微秒，音频时间轴）。 */
    val endUs: Long,
    /** 出结果时已经晚于 [endUs] 的时长（微秒）。 */
    val lateUs: Long,
    /**
     * 该句音频尚未被播出的毫秒数（解码预读量）。
     *
     * <p>字幕必须延迟这么久再显示才能与语音对齐——只开预读不做延迟，字幕会大幅超前。
     * 负值表示无法测量（此时按 0 处理，即立即显示）。
     */
    val lookaheadMs: Long,
    /**
     * 本条字幕创建时刻（`SystemClock.elapsedRealtime()`）。
     *
     * <p>开启翻译时，字幕要等翻译返回才投递，期间已经消耗掉一部分预读余量；
     * UI 用「当前时刻 − createdAtMs」扣减剩余等待，保证仍然按语音时间上屏。
     */
    val createdAtMs: Long = 0L,
) {
    /** 语音本身的时长（毫秒）。 */
    val speechMs: Long
        get() = if (endUs > startUs) (endUs - startUs) / 1000 else 0L

    /**
     * 从「识别完成」到「应当上屏」需要等待的毫秒数。
     *
     * <p>= 预读量 − 本句已说完的时长。若为负说明这句已经该被听到了，立即显示。
     */
    val startDelayMs: Long
        get() = (lookaheadMs - speechMs).coerceAtLeast(0L)

    /**
     * 该句应停留的时长（毫秒）。
     *
     * <p>算法与上游一致：`min(6500, 语音时长 + 500)`，下限 900ms。
     */
    val holdMs: Long
        get() = (speechMs + 500).coerceIn(900L, 6500L)
}

/**
 * AI 字幕显示样式（设置面板与播放器弹窗共用同一套参数）。
 *
 * @property offsetDp  垂直偏移（正数向上），范围 -40..80
 * @property color     0 白 / 1 红 / 2 绿 / 3 黄 / 4 随机
 * @property sizeScale 字号倍率 0.8..1.6
 * @property bold      加粗
 * @property stroke    0 阴影 / 1 描边 / 2 无
 * @property font      0 默认 / 1 圆体 / 2 衬线
 */
data class AiSubtitleStyle(
    val offsetDp: Int = 0,
    val color: Int = 0,
    val sizeScale: Float = 1f,
    val bold: Boolean = false,
    val stroke: Int = 0,
    val font: Int = 0,
)
/** 一个可选识别语种（设置页「识别语言」用）。 */
data class AiLanguageOption(val code: String, val label: String)

/**
 * AI 字幕翻译配置（设置页读写，播放时由 bridge 读取）。
 *
 * @property enabled   翻译总开关（默认关）
 * @property provider  0 有道公开端点 / 1 MTranServer / 2 大模型（OpenAI 兼容）
 * @property mode      0 双语（译文 + 原文）/ 1 仅译文
 */
data class AiTranslationConfig(
    val enabled: Boolean = false,
    val provider: Int = 0,
    val mode: Int = 0,
    val mtranName: String = "",
    val mtranUrl: String = "",
    val mtranToken: String = "",
    val llmName: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val thinking: Boolean = false,
    val context: Boolean = false,
)

/**
 * AI 字幕（实时语音识别 + 翻译）的桥接接口。
 *
 * <p>UI 只依赖本接口：TV/手机由宿主注入实现；LITE 不注入（保持 null），
 * 设置页与播放器菜单据此自动隐藏入口，避免 LITE 被迫打包 sherpa-onnx。
 */
interface AiSubtitleBridge {

    /** 引擎是否可用（依赖或模型缺失时为 false，UI 显示为不可用）。 */
    val available: Boolean

    /** 当前是否已开启（普通读）。 */
    fun isEnabled(): Boolean

    /** 开关状态（Compose 可观察）：播放中切换也能立即生效。 */
    val enabledState: State<Boolean>

    /** 切换开关（即时生效：抽头常驻，引擎按开关懒启停，无需重播）。 */
    fun setEnabled(enabled: Boolean)

    /** 打开完整设置页（宿主负责导航）。 */
    fun openSettings()

    /**
     * 订阅 AI 字幕定稿（句末，整句一次性输出）。
     *
     * <p>回调发生在识别线程，实现方应自行切到主线程再更新 UI。
     */
    fun observeCues(sink: (AiSubtitleCue) -> Unit): AutoCloseable

    // ---- 显示样式（P6）----

    /** 读取当前字幕样式。 */
    fun subtitleStyle(): AiSubtitleStyle

    /** 写入字幕样式（设置面板与播放器弹窗共用）。 */
    fun setSubtitleStyle(style: AiSubtitleStyle)

    /** 可观察的字幕样式：播放器弹窗改动后字幕立即生效。 */
    val subtitleStyleState: State<AiSubtitleStyle>
    // ---- 识别语种（P6）----

    /** 当前识别语种代码（zh/en/de/fr/es）。 */
    fun languageCode(): String

    /** 可选语种列表。 */
    fun languageOptions(): List<AiLanguageOption>

    /** 切换识别语种（会释放已加载的模型，下次开启按新语种加载）。 */
    fun setLanguage(code: String)

    // ---- 翻译配置（P5）----

    /** 读取翻译配置。 */
    fun translationConfig(): AiTranslationConfig

    fun setTranslationEnabled(enabled: Boolean)

    fun setTranslationProvider(provider: Int)

    fun setSubtitleMode(mode: Int)

    fun setMTranUrl(url: String)

    fun setMTranToken(token: String)

    fun setMTranName(name: String)

    fun setApiBaseUrl(url: String)

    fun setApiKey(key: String)

    fun setApiModel(model: String)

    fun setApiName(name: String)

    fun setThinkingEnabled(enabled: Boolean)

    fun setContextEnabled(enabled: Boolean)

    /**
     * 测试当前翻译通道（用固定英文样本），回调在主线程。
     *
     * @param onResult 成功与否 + 详情（成功时含译文与耗时）
     */
    fun testTranslation(onResult: (ok: Boolean, detail: String) -> Unit)

    /**
     * 测试**指定**通道（不改动当前选中的翻译服务）。
     *
     * <p>用于「网络接口」里 MTranServer / 大模型 行尾的「加载」按钮。
     *
     * @param provider 0 有道 / 1 MTranServer / 2 大模型
     */
    fun testTranslationProvider(provider: Int, onResult: (ok: Boolean, detail: String) -> Unit)
}
