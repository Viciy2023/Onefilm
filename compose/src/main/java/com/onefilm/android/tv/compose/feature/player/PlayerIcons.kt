package com.onefilm.android.tv.compose.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class PlayerGlyph {
    Play, Pause, Previous, Next, Fullscreen, ExitFullscreen, More, Episodes, Back, Subtitle, Audio,
}

/** 纯 Canvas 图标，避免依赖图标字体在部分设备缺字。 */
@Composable
fun PlayerIcon(
    glyph: PlayerGlyph,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = size.minDimension * 0.09f)
        when (glyph) {
            PlayerGlyph.Play -> {
                val path = Path().apply {
                    moveTo(w * 0.32f, h * 0.18f)
                    lineTo(w * 0.82f, h * 0.5f)
                    lineTo(w * 0.32f, h * 0.82f)
                    close()
                }
                drawPath(path, tint)
            }
            PlayerGlyph.Pause -> {
                drawRoundRect(tint, topLeft = Offset(w * 0.30f, h * 0.18f), size = Size(w * 0.14f, h * 0.64f))
                drawRoundRect(tint, topLeft = Offset(w * 0.56f, h * 0.18f), size = Size(w * 0.14f, h * 0.64f))
            }
            PlayerGlyph.Previous -> {
                drawRect(tint, topLeft = Offset(w * 0.24f, h * 0.2f), size = Size(w * 0.08f, h * 0.6f))
                val path = Path().apply {
                    moveTo(w * 0.78f, h * 0.2f)
                    lineTo(w * 0.78f, h * 0.8f)
                    lineTo(w * 0.34f, h * 0.5f)
                    close()
                }
                drawPath(path, tint)
            }
            PlayerGlyph.Next -> {
                drawRect(tint, topLeft = Offset(w * 0.68f, h * 0.2f), size = Size(w * 0.08f, h * 0.6f))
                val path = Path().apply {
                    moveTo(w * 0.22f, h * 0.2f)
                    lineTo(w * 0.22f, h * 0.8f)
                    lineTo(w * 0.66f, h * 0.5f)
                    close()
                }
                drawPath(path, tint)
            }
            PlayerGlyph.Fullscreen, PlayerGlyph.ExitFullscreen -> {
                val inset = if (glyph == PlayerGlyph.Fullscreen) 0.18f else 0.34f
                val len = 0.22f
                val l = w * inset
                val t = h * inset
                val r = w * (1f - inset)
                val b = h * (1f - inset)
                // 左上
                drawLine(tint, Offset(l, t), Offset(l + w * len, t), stroke.width)
                drawLine(tint, Offset(l, t), Offset(l, t + h * len), stroke.width)
                // 右上
                drawLine(tint, Offset(r, t), Offset(r - w * len, t), stroke.width)
                drawLine(tint, Offset(r, t), Offset(r, t + h * len), stroke.width)
                // 左下
                drawLine(tint, Offset(l, b), Offset(l + w * len, b), stroke.width)
                drawLine(tint, Offset(l, b), Offset(l, b - h * len), stroke.width)
                // 右下
                drawLine(tint, Offset(r, b), Offset(r - w * len, b), stroke.width)
                drawLine(tint, Offset(r, b), Offset(r, b - h * len), stroke.width)
            }
            PlayerGlyph.More -> {
                val radius = w * 0.07f
                drawCircle(tint, radius, Offset(w * 0.24f, h * 0.5f))
                drawCircle(tint, radius, Offset(w * 0.5f, h * 0.5f))
                drawCircle(tint, radius, Offset(w * 0.76f, h * 0.5f))
            }
            PlayerGlyph.Episodes -> {
                val cell = w * 0.28f
                val gap = w * 0.12f
                val start = w * 0.16f
                for (row in 0..1) {
                    for (col in 0..1) {
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(start + col * (cell + gap), start + row * (cell + gap)),
                            size = Size(cell, cell),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
                        )
                    }
                }
            }
            PlayerGlyph.Back -> {
                drawLine(tint, Offset(w * 0.62f, h * 0.24f), Offset(w * 0.36f, h * 0.5f), stroke.width)
                drawLine(tint, Offset(w * 0.36f, h * 0.5f), Offset(w * 0.62f, h * 0.76f), stroke.width)
            }
            PlayerGlyph.Subtitle -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.26f),
                    size = Size(w * 0.68f, h * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
                    style = stroke,
                )
                drawLine(tint, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.46f, h * 0.52f), stroke.width * 0.8f)
                drawLine(tint, Offset(w * 0.54f, h * 0.52f), Offset(w * 0.72f, h * 0.52f), stroke.width * 0.8f)
                drawLine(tint, Offset(w * 0.28f, h * 0.64f), Offset(w * 0.62f, h * 0.64f), stroke.width * 0.8f)
            }
            PlayerGlyph.Audio -> {
                val path = Path().apply {
                    moveTo(w * 0.18f, h * 0.38f)
                    lineTo(w * 0.34f, h * 0.38f)
                    lineTo(w * 0.50f, h * 0.24f)
                    lineTo(w * 0.50f, h * 0.76f)
                    lineTo(w * 0.34f, h * 0.62f)
                    lineTo(w * 0.18f, h * 0.62f)
                    close()
                }
                drawPath(path, tint)
                drawArc(
                    color = tint,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.46f, h * 0.30f),
                    size = Size(w * 0.36f, h * 0.40f),
                    style = stroke,
                )
                drawArc(
                    color = tint.copy(alpha = 0.6f),
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(w * 0.54f, h * 0.20f),
                    size = Size(w * 0.32f, h * 0.60f),
                    style = stroke,
                )
            }
        }
    }
}
