package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.PanLogEntry
import com.onefilm.android.tv.compose.bridge.PanSetting
import com.onefilm.android.tv.compose.bridge.PanSettings
import com.onefilm.android.tv.compose.bridge.statusValue
import kotlinx.coroutines.launch

/** 弹窗内的可选中行：与按钮同一套焦点/选中表现，遥控器可直接上下移动。 */
@Composable
private fun PanSelectRow(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (focused) 1f else 0.85f)
        focused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val foreground = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
            )
            .onFocusChanged { if (enabled) focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 单选型设置弹窗：播放选项 / 多线程代理 / 自动弹幕 / Go DNS / 鸿蒙兼容。 */
@Composable
fun PanChoiceDialog(
    siteKey: String,
    setting: PanSetting.Choice,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(siteKey, setting.field) {
        picked = actions?.statusValue(siteKey, setting.field)
        loading = false
    }
    AppDialog(
        title = setting.title,
        subtitle = setting.subtitle,
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                enabled = !saving,
                autoFocus = true,
                onClick = onDone,
            )
            AppDialogAction(
                label = stringResource(R.string.pan_save),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !saving && !loading && picked != null,
                onClick = {
                    val value = picked ?: return@AppDialogAction
                    saving = true
                    scope.launch {
                        actions?.runAction(
                            siteKey,
                            "action=" + setting.setter + "&value=" + android.net.Uri.encode(value),
                        )
                        saving = false
                        onDone()
                    }
                },
            )
        },
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            setting.options.forEach { (value, label) ->
                PanSelectRow(
                    label = label,
                    selected = picked == value,
                    enabled = !saving,
                ) { picked = value }
            }
        }
    }
}

/** 网盘线路排序弹窗：选中一条后上移/下移，保存后写回接口。 */
@Composable
fun PanOrderDialog(
    siteKey: String,
    title: String,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val scope = rememberCoroutineScope()
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(siteKey) {
        val raw = actions?.statusValue(siteKey, "panOrder").orEmpty()
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "direct" }
        order = parsed.ifEmpty {
            listOf("quark", "uc", "baidu", "pan115", "pan123", "pan189", "pan139", "guangya", "thunder")
        }
        index = 0
        loading = false
    }
    fun move(step: Int) {
        if (order.isEmpty()) return
        val target = index + step
        if (target < 0 || target > order.lastIndex) return
        val next = order.toMutableList()
        val item = next.removeAt(index)
        next.add(target, item)
        order = next
        index = target
    }
    AppDialog(
        title = title,
        subtitle = "先调整顺序，点保存后才会影响所有线路的显示顺序。",
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                enabled = !saving,
                autoFocus = true,
                onClick = onDone,
            )
            AppDialogAction(
                label = "上移",
                modifier = Modifier.weight(1f),
                enabled = !saving && !loading && index > 0,
                onClick = { move(-1) },
            )
            AppDialogAction(
                label = "下移",
                modifier = Modifier.weight(1f),
                enabled = !saving && !loading && order.isNotEmpty() && index < order.lastIndex,
                onClick = { move(1) },
            )
            AppDialogAction(
                label = stringResource(R.string.pan_save),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !saving && !loading && order.isNotEmpty(),
                onClick = {
                    saving = true
                    scope.launch {
                        val value = (order + "direct").joinToString(",")
                        actions?.runAction(
                            siteKey,
                            "action=setPanOrder&value=" + android.net.Uri.encode(value),
                        )
                        saving = false
                        onDone()
                    }
                },
            )
        },
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else {
            order.forEachIndexed { position, key ->
                PanSelectRow(
                    label = (position + 1).toString() + ". " + (PanSettings.orderLabels[key] ?: key),
                    selected = position == index,
                    enabled = !saving,
                ) { index = position }
            }
        }
    }
}

/** 日志列表弹窗：只读展示接口返回的日志条目。 */
@Composable
fun PanLogsDialog(
    siteKey: String,
    title: String,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    var entries by remember { mutableStateOf<List<PanLogEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(siteKey, reload) {
        loading = true
        entries = actions?.logs(siteKey).orEmpty()
        loading = false
    }
    AppDialog(
        title = title,
        subtitle = "日志来自接口的 logs 目录，共 " + entries.size + " 个文件。",
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.pan_refresh),
                modifier = Modifier.weight(1f),
                enabled = !loading,
                autoFocus = true,
                onClick = { reload++ },
            )
            AppDialogAction(
                label = stringResource(R.string.pan_close),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !loading,
                onClick = onDone,
            )
        },
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        } else if (entries.isEmpty()) {
            Text("暂无日志文件", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            entries.forEach { entry ->
                Column(Modifier.fillMaxWidth()) {
                    Text(entry.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = listOf(entry.size, entry.time).filter { it.isNotBlank() }.joinToString(" · "),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
