package com.onefilm.android.tv.compose.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.AiSubtitleBridge
import com.onefilm.android.tv.compose.bridge.AiSubtitleStyle

/**
 * 播放器内「AI字幕」面板的条目。
 *
 * <p>与设置面板共用同一套参数（开关/语种/翻译/样式），点击即在当前面板内切换或循环，
 * 不需要退出播放器。识别模型的下载入口仍在设置面板，这里点击只给引导提示。
 *
 * <p>条目复用弹幕面板的 [DanmakuItem] + [DanmakuItemGrid]，样式完全一致（无标题、无关闭按钮）。
 */
@Composable
internal fun buildAiSubtitleItems(
    bridge: AiSubtitleBridge,
    style: AiSubtitleStyle,
    onStyleChange: (AiSubtitleStyle) -> Unit,
    onModelTap: () -> Unit,
    onChanged: () -> Unit,
): List<DanmakuItem> {
    val context = LocalContext.current
    fun s(id: Int, vararg args: Any): String = context.getString(id, *args)
    val on = s(R.string.common_on)
    val off = s(R.string.common_off)

    /** 点击后把值切到下一个（到末尾回到第一个），与弹幕面板一致。 */
    fun <T> cycle(values: List<T>, current: T, apply: (T) -> Unit): () -> Unit = {
        val index = values.indexOf(current)
        val next = if (index < 0) values.first() else values[(index + 1) % values.size]
        apply(next)
    }

    val cfg = bridge.translationConfig()
    val languages = bridge.languageOptions()
    val langCode = bridge.languageCode()
    val langLabel = languages.firstOrNull { it.code == langCode }?.label ?: langCode
    val languagesList = languages.map { it.code }

    val providerLabels = listOf(
        0 to s(R.string.settings_ai_subtitle_provider_default),
        1 to s(R.string.settings_ai_subtitle_provider_mtran),
        2 to s(R.string.settings_ai_subtitle_provider_openai),
        3 to s(R.string.settings_ai_subtitle_provider_tencent),
        4 to s(R.string.settings_ai_subtitle_provider_bing),
    )
    val providerText = providerLabels.firstOrNull { it.first == cfg.provider }?.second ?: ""
    val modeText = if (cfg.mode == 1) s(R.string.settings_ai_subtitle_mode_translated)
    else s(R.string.settings_ai_subtitle_mode_bilingual)

    val positions = listOf(-40, -20, 0, 20, 40, 60)
    val colorIds = listOf(
        R.string.ai_color_white,
        R.string.ai_color_red,
        R.string.ai_color_green,
        R.string.ai_color_yellow,
        R.string.ai_color_random,
    )
    val sizes = listOf(0.8f, 1.0f, 1.2f, 1.4f, 1.6f)
    val strokeIds = listOf(R.string.ai_stroke_shadow, R.string.ai_stroke_outline, R.string.ai_stroke_none)
    val fontIds = listOf(R.string.ai_font_default, R.string.ai_font_round, R.string.ai_font_serif)

    return listOf(
        DanmakuItem(
            label = "${s(R.string.settings_ai_subtitle_enabled)} ${if (bridge.isEnabled()) on else off}",
            onClick = {
                bridge.setEnabled(!bridge.isEnabled())
                onChanged()
            },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_language)} $langLabel",
            onClick = cycle(languagesList, langCode) {
                bridge.setLanguage(it)
                onChanged()
            },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_subtitle_model)} " +
                if (bridge.available) s(R.string.settings_ai_subtitle_on) else s(R.string.settings_ai_subtitle_off),
            onClick = onModelTap,
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_subtitle_translate)} ${if (cfg.enabled) on else off}",
            onClick = {
                bridge.setTranslationEnabled(!cfg.enabled)
                onChanged()
            },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_subtitle_provider)} $providerText",
            onClick = cycle(listOf(0, 1, 2, 3, 4), cfg.provider) {
                bridge.setTranslationProvider(it)
                onChanged()
            },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_subtitle_mode)} $modeText",
            onClick = {
                bridge.setSubtitleMode(if (cfg.mode == 0) 1 else 0)
                onChanged()
            },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_position)} ${if (style.offsetDp > 0) "+${style.offsetDp}" else style.offsetDp}",
            onClick = cycle(positions, style.offsetDp) { onStyleChange(style.copy(offsetDp = it)) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_color)} ${s(colorIds.getOrElse(style.color) { colorIds[0] })}",
            onClick = cycle(listOf(0, 1, 2, 3, 4), style.color) { onStyleChange(style.copy(color = it)) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_size)} ${"%.1fx".format(style.sizeScale)}",
            onClick = cycle(sizes, style.sizeScale) { onStyleChange(style.copy(sizeScale = it)) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_stroke)} ${s(strokeIds.getOrElse(style.stroke) { strokeIds[0] })}",
            onClick = cycle(listOf(0, 1, 2), style.stroke) { onStyleChange(style.copy(stroke = it)) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_bold)} ${if (style.bold) on else off}",
            onClick = { onStyleChange(style.copy(bold = !style.bold)) },
        ),
        DanmakuItem(
            label = "${s(R.string.settings_ai_style_font)} ${s(fontIds.getOrElse(style.font) { fontIds[0] })}",
            onClick = cycle(listOf(0, 1, 2), style.font) { onStyleChange(style.copy(font = it)) },
        ),
    )
}
