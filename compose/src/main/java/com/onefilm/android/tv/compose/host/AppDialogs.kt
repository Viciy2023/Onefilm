package com.onefilm.android.tv.compose.host

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * 退出确认使用独立的小卡片，而不是平台默认 AlertDialog。
 * 外间距只保留 12dp，标题/正文/按钮行距压缩，卡片在屏幕正中。
 */
@Composable
fun ExitAppDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
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
                modifier = Modifier.widthIn(min = 340.dp, max = 440.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                ) {
                    Text(
                        text = "退出壹影",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "退出后将停止当前播放，确定要关闭应用吗？",
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DialogAction(
                            label = "取消",
                            focusRequester = cancelFocus,
                            primary = false,
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss,
                        )
                        DialogAction(
                            label = "退出",
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { cancelFocus.requestFocus() }
    }
}

@Composable
fun UpdatePromptDialog(
    version: String,
    notes: String,
    progress: Int,
    error: String?,
    force: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val autoStarted = remember { mutableStateOf(false) }
    val downloading = progress >= 0
    Dialog(
        onDismissRequest = { if (!force && !downloading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = !force,
        ),
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
                modifier = Modifier.widthIn(min = 360.dp, max = 480.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = "发现新版本 · $version",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (force) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "当前已启用强制升级，更新完成后才能继续使用。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = notes,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .heightIn(max = 170.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (progress.coerceIn(0, 100)) / 100f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
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
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (!force) {
                            DialogAction(
                                label = "稍后",
                                focusRequester = cancelFocus,
                                enabled = !downloading,
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss,
                            )
                        }
                        DialogAction(
                            label = if (downloading) "下载中 $progress%" else "立即更新",
                            focusRequester = if (force) confirmFocus else null,
                            enabled = !downloading,
                            primary = true,
                            modifier = Modifier.weight(1f),
                            onClick = onConfirm,
                        )
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching {
            if (force) confirmFocus.requestFocus() else cancelFocus.requestFocus()
        }
    }
    LaunchedEffect(force) {
        if (force && !autoStarted.value) {
            autoStarted.value = true
            onConfirm()
        }
    }
}

@Composable
private fun DialogAction(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        !enabled && primary -> MaterialTheme.colorScheme.surfaceVariant
        focused && primary -> MaterialTheme.colorScheme.primary
        focused -> MaterialTheme.colorScheme.surfaceVariant
        primary -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    }
    val foreground = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        focused && primary -> MaterialTheme.colorScheme.onPrimary
        primary -> MaterialTheme.colorScheme.background
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier
            .height(42.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
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
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Medium else FontWeight.Normal,
            color = foreground,
            maxLines = 1,
        )
    }
}
