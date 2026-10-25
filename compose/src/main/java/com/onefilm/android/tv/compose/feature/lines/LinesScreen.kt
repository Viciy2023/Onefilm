package com.onefilm.android.tv.compose.feature.lines

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import kotlinx.coroutines.delay

@Composable
fun LinesScreen(
    dataSource: SettingsDataSource,
    onDone: () -> Unit,
    onConfigChanged: () -> Unit = {},
) {
    var lines by remember { mutableStateOf(dataSource.allConfigLines()) }
    var loadingKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstLineFocus = remember { FocusRequester() }

    fun refresh() {
        lines = dataSource.allConfigLines()
    }

    LaunchedEffect(Unit) { refresh() }

    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) {
            delay(80)
            runCatching { firstLineFocus.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = { if (loadingKey == null) onDone() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(460.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        // 10 条紧凑线路不滚动可见；第 11 条起才需要滚动。
                        .height(312.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.55f),
                            RoundedCornerShape(12.dp),
                        )
                        .padding(4.dp),
                ) {
                    if (lines.isEmpty()) {
                        Text(
                            text = "暂无线路，请先在配置弹窗中添加。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    } else {
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            lines.forEachIndexed { index, line ->
                                LineRow(
                                    line = line,
                                    loading = loadingKey == line.key(),
                                    enabled = loadingKey == null,
                                    focusRequester = if (index == 0) firstLineFocus else null,
                                    onPick = {
                                        if (loadingKey == null) {
                                            loadingKey = line.key()
                                            error = null
                                            dataSource.loadConfig(line.type, line.url, line.name) { ok, message ->
                                                loadingKey = null
                                                if (ok) {
                                                    onConfigChanged()
                                                    onDone()
                                                } else {
                                                    error = message
                                                }
                                            }
                                        }
                                    },
                                    onDelete = {
                                        if (loadingKey == null) {
                                            dataSource.deleteConfigLine(line.type, line.url)
                                            refresh()
                                            onConfigChanged()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LineRow(
    line: ConfigLine,
    loading: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester? = null,
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(
                when {
                    line.current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    focused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                },
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> MaterialTheme.colorScheme.primary
                    line.current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                    else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                },
                shape = shape,
            )
            .clickable(enabled = enabled && !loading, onClick = onPick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (loading) "加载中…" else line.name.ifBlank { line.url },
            fontSize = 13.sp,
            fontWeight = if (line.current) FontWeight.SemiBold else FontWeight.Medium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (line.current) {
            Text("当前", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
        }
        if (line.type == 1) {
            Text("直播", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = "删除",
            fontSize = 12.sp,
            color = if (enabled && !loading) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = enabled && !loading, onClick = onDelete)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

private fun ConfigLine.key(): String = "$type|$url|$name"
