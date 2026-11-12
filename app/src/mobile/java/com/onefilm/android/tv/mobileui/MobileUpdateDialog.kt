package com.onefilm.android.tv.mobileui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.R

/**
 * 手机端更新弹窗（竖屏 / 触控）。
 *
 * 与 TV 的 `UpdatePromptDialog` 完全独立：TV 版本是遥控器焦点式全屏布局，
 * 这里用 Material3 AlertDialog，按钮热区按手指尺寸设计，不共用任何 TV 布局或焦点逻辑。
 *
 * `force = true` 时不提供任何关闭入口：返回键、点击外部、「稍后」按钮全部禁用，
 * 必须下载安装后才能继续使用。
 */
@Composable
fun MobileUpdateDialog(
    version: String,
    notes: String,
    progress: Int,
    error: String?,
    force: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val downloading = progress >= 0
    val percent = progress.coerceIn(0, 100)
    AlertDialog(
        onDismissRequest = { if (!force && !downloading) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !force && !downloading,
            dismissOnClickOutside = false,
        ),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.app_update_title, version),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (force) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_update_force),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        text = {
            Column {
                if (notes.isNotBlank()) {
                    Text(
                        text = notes,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (downloading) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.app_update_downloading, percent),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!error.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !downloading) {
                Text(stringResource(R.string.app_update_now))
            }
        },
        dismissButton = if (force) {
            null
        } else {
            {
                TextButton(onClick = onDismiss, enabled = !downloading) {
                    Text(stringResource(R.string.app_update_later))
                }
            }
        },
    )
}
