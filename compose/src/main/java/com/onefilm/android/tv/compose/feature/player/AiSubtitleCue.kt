package com.onefilm.android.tv.compose.feature.player

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.AiSubtitleStyle
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 订阅 AI 字幕文本（**整句一次性出现**，不做打字机式 partial）。
 *
 * <p>关键：解码是预读的，识别完成时该句语音还没被播出，因此必须延迟
 * [com.onefilm.android.tv.compose.bridge.AiSubtitleCue.startDelayMs] 毫秒再上屏，
 * 才能与人物口型对齐；停留 [com.onefilm.android.tv.compose.bridge.AiSubtitleCue.holdMs]。
 *
 * <p>只做数据订阅与排程，不含布局：TV 与 Mobile 各自决定摆在哪、字号多大。
 */
@Composable
fun rememberAiSubtitleCue(): String? {
    var text by remember { mutableStateOf<String?>(null) }
    var shownSeq by remember { mutableStateOf(0) }
    val counter = remember { AtomicInteger(0) }
    val scope = rememberCoroutineScope()
    val bridge = ComposeDependencies.aiSubtitle

    // 读 enabledState 让本 composable 订阅开关变化：播放中开关切换会重启下面的 effect
    val enabled = bridge?.enabledState?.value ?: false
    LaunchedEffect(bridge, enabled) {
        if (bridge == null || !enabled) return@LaunchedEffect
        val handler = Handler(Looper.getMainLooper())
        val closable = bridge.observeCues { cue ->
            handler.post {
                scope.launch {
                    val mySeq = counter.incrementAndGet()
                    // 预读量 − 本句已说完的时长 − 等待翻译已消耗的时间；
                    // 为 0 表示已经该被听到了，立即显示
                    val ageMs = android.os.SystemClock.elapsedRealtime() - cue.createdAtMs
                    val remaining = cue.startDelayMs - ageMs
                    // 过晚（超过 1.5s）就丢弃：宁可少一句，也不要压住下一句造成叠字
                    if (remaining < -1500L) return@launch
                    if (remaining > 0) delay(remaining)
                    shownSeq = mySeq
                    text = cue.text
                    delay(cue.holdMs)
                    // 只有仍显示本条时才清空，避免把后来的新句清掉
                    if (shownSeq == mySeq) text = null
                }
            }
        }
        try {
            awaitCancellation()
        } finally {
            closable.close()
        }
    }
    return text
}

/**
 * TV 端 AI 字幕：白字 + 黑色文字阴影，**不画底框**。
 *
 * <p>按容器宽度自适应：全屏 34sp，小窗/手机 15sp。TV 与手机共用这一份实现，
 * 双语字幕（译文 + 原文，用 `\n` 分隔）自动缩小一号，避免两行顶满屏幕。
 */
/**
 * 字幕来源提示：**前 3 条** AI 字幕在正文上方显示一行「AI 字幕 · 语言 → 翻译服务」，
 * 之后自动隐藏；语种或翻译服务变化时重新计 3 条。
 *
 * <p>目的：用户能确认当前用的是哪家翻译（有道 / MTranServer / 大模型），
 * 而不是只能靠日志判断。
 */
@Composable
fun rememberAiSubtitleSourceCaption(cue: String?): String? {
    val bridge = ComposeDependencies.aiSubtitle
    var shown by remember { mutableStateOf(0) }
    var last by remember { mutableStateOf("") }
    val caption = if (bridge == null) {
        ""
    } else {
        runCatching {
            val cfg = bridge.translationConfig()
            val lang = bridge.languageOptions().firstOrNull { it.code == bridge.languageCode() }?.label.orEmpty()
            val provider = when (cfg.provider) {
                1 -> stringResource(R.string.settings_ai_subtitle_provider_mtran)
                2 -> stringResource(R.string.settings_ai_subtitle_provider_openai)
                3 -> stringResource(R.string.settings_ai_subtitle_provider_tencent)
                4 -> stringResource(R.string.settings_ai_subtitle_provider_bing)
                else -> stringResource(R.string.settings_ai_subtitle_provider_default)
            }
            if (cfg.enabled) "AI 字幕 · $lang → $provider" else "AI 字幕 · $lang"
        }.getOrDefault("")
    }
    LaunchedEffect(cue, caption) {
        if (cue == null || caption.isBlank()) return@LaunchedEffect
        if (caption != last) {
            last = caption
            shown = 0
        }
        if (shown < 3) shown++
    }
    return if (cue != null && caption.isNotBlank() && shown < 3) caption else null
}
@Composable
fun AiSubtitleOverlay(
    cue: String?,
    modifier: Modifier = Modifier,
    /** 小窗/手机端的底部留白（手机要避开控制栏与手势条，传 96）。 */
    compactBottomDp: Int = 48,
) {
    // 必须在 cue 为空的间隙也调用：否则 remember 状态会被销毁，提示会每句都重新出现
    val caption = rememberAiSubtitleSourceCaption(cue)
    if (cue.isNullOrBlank()) return
    val style = ComposeDependencies.aiSubtitle?.subtitleStyleState?.value ?: AiSubtitleStyle()
    val bilingual = cue.contains('\n')
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        val compact = maxWidth < 700.dp
        val baseSize = when {
            compact -> 15f
            bilingual -> 27f
            else -> 34f
        }
        val fontSize = (baseSize * style.sizeScale).sp
        val lineHeight = (fontSize.value * 1.30f).sp
        val textColor = aiSubtitleColor(style.color, cue)
        val weight = if (style.bold) FontWeight.Bold else FontWeight.Normal
        val family = when (style.font) {
            1 -> FontFamily.SansSerif
            2 -> FontFamily.Serif
            else -> FontFamily.Default
        }
        val shadow = when (style.stroke) {
            1 -> Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 14f)
            2 -> null
            else -> Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = if (compact) 5f else 8f)
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 80.dp,
                // 正数 offsetDp = 往上移；底部安全距离与硬字幕错开
                vertical = ((if (compact) compactBottomDp else 40) + style.offsetDp).coerceAtLeast(0).dp,
            ),
        ) {
            if (caption != null) {
                Text(
                    text = caption,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = (if (compact) 10f else 13f).sp,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = Shadow(color = Color.Black, offset = Offset.Zero, blurRadius = 5f)),
                )
                Spacer(Modifier.height(4.dp))
            }
            if (style.stroke == 1) {
                // 描边：4 个方向的黑色副本垫底，再叠正常颜色
                Box(contentAlignment = Alignment.Center) {
                    val d = 2.dp
                    listOf(-d to 0.dp, d to 0.dp, 0.dp to -d, 0.dp to d).forEach { (dx, dy) ->
                        Text(
                            text = cue,
                            color = Color.Black,
                            fontSize = fontSize,
                            lineHeight = lineHeight,
                            fontWeight = weight,
                            fontFamily = family,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.offset(dx, dy),
                        )
                    }
                    Text(
                        text = cue,
                        color = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        fontWeight = weight,
                        fontFamily = family,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Text(
                    text = cue,
                    color = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = weight,
                    fontFamily = family,
                    textAlign = TextAlign.Center,
                    style = TextStyle(shadow = shadow),
                )
            }
        }
    }
}

/** 0 白 / 1 红 / 2 绿 / 3 黄 / 4 随机（按句子散列，稳定不闪）。 */
private fun aiSubtitleColor(mode: Int, cue: String): Color = when (mode) {
    1 -> Color(0xFFFF5252)
    2 -> Color(0xFF69F0AE)
    3 -> Color(0xFFFFD740)
    4 -> when (kotlin.math.abs(cue.hashCode()) % 3) {
        0 -> Color(0xFFFF5252)
        1 -> Color(0xFF69F0AE)
        else -> Color(0xFFFFD740)
    }
    else -> Color.White
}
