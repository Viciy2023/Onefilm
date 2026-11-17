package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 网盘配置弹窗里的输入行容器。
 *
 * 视觉规格**完全对齐「导航设置」面板**（`SettingsScreen.SettingsPanelRow`）：
 * 最小 34dp 高、10dp 圆角、surfaceVariant 底、1dp outlineVariant 边框，
 * 聚焦时换成主色 2dp 边框 + surfaceVariant 实底。
 *
 * 这里做成公开组件，是为了让配置中心的所有弹窗与设置面板共用同一套外观，
 * 不再各自使用 Material3 `OutlinedTextField` 那套默认样式。
 */
@Composable
fun PanFieldRow(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.CenterStart,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            ),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * 与「导航设置」面板 `ConfigTextField` 同规格的单行输入框：
 * 14sp 正文、13sp 占位、横向 12dp / 纵向 9dp 内边距。
 */
@Composable
fun PanTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
) {
    PanFieldRow(
        modifier = modifier,
        contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (singleLine) Modifier else Modifier.fillMaxHeight())
                .padding(horizontal = 12.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    if (value.isBlank()) {
                        Text(
                            placeholder,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}
