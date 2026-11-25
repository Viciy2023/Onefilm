package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * App 统一弹窗层：所有功能弹窗都走这里。
 *
 * 样式与 App 现有的「退出确认 / 更新提示」等弹窗完全一致：16dp 圆角、主题 surface 背景、
 * outline 描边、20sp 标题、14sp 正文、底部整行按钮；颜色全部取 MaterialTheme，随 App 主题变化。
 *
 * 结构：标题 + 可选副标题 + 可滚动内容 + 固定在底部的按钮行。
 * 按钮行不参与滚动，因此任何屏幕高度下都不会被裁掉。
 */
@Composable
fun AppDialog(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val config = LocalConfiguration.current
    val touch = config.screenWidthDp < 600
    val maxWidth = if (touch) 440.dp else 560.dp
    val fraction = if (touch) 0.92f else 0.62f
    // 最多占屏高 86%，保证底部按钮始终可见
    val maxHeight = (config.screenHeightDp * 0.86f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                ),
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(
                        Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        content()
                    }
                    if (actions != null) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            content = actions,
                        )
                    }
                }
            }
        }
    }
}
