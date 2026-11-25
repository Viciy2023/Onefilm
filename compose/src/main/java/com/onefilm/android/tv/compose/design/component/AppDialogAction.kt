package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * 统一弹窗里的按钮：和「退出确认」「更新提示」等弹窗同一个样式与焦点表现。
 *
 * @param autoFocus 打开弹窗后自动把遥控器焦点放到这个按钮上（TV 端必需）。
 */
@Composable
fun AppDialogAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    val internal = remember { FocusRequester() }
    val requester = focusRequester ?: internal
    var focused by remember { mutableStateOf(false) }
    val highlighted = enabled && (focused || primary)
    val brush = when {
        !enabled -> androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
        highlighted -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onSurface),
        )
        else -> androidx.compose.ui.graphics.Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surface,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            ),
        )
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        highlighted -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .height(32.dp)
            .focusRequester(requester)
            .clip(RoundedCornerShape(10.dp))
            .background(brush)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { if (enabled) focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = foreground,
            maxLines = 1,
        )
    }
    if (autoFocus) {
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { requester.requestFocus() }
        }
    }
}
