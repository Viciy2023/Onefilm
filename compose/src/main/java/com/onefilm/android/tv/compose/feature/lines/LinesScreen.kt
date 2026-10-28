package com.onefilm.android.tv.compose.feature.lines

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Color
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
    var depotName by remember { mutableStateOf(dataSource.depotName()) }
    fun currentLines(): List<ConfigLine> =
        dataSource.depotLines().ifEmpty { dataSource.allConfigLines() }
    var lines by remember { mutableStateOf(currentLines()) }
    var loadingKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val firstLineFocus = remember { FocusRequester() }

    fun refresh() {
        depotName = dataSource.depotName()
        lines = currentLines()
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
                Text(
                    text = if (depotName.isBlank()) "线路" else "仓库：$depotName",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
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
                            text = if (depotName.isBlank()) "暂无线路，请先在配置弹窗中添加。" else "该仓库没有可选线路",
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
                                            val onResult: (Boolean, String) -> Unit = { ok, message ->
                                                loadingKey = null
                                                if (ok) {
                                                    onConfigChanged()
                                                    onDone()
                                                } else {
                                                    error = message
                                                }
                                            }
                                            // 直播线路必须走直播配置加载，否则会被当成点播仓库线路
                                            when {
                                                line.type == 1 -> dataSource.loadConfig(1, line.url, line.name, onResult)
                                                depotName.isNotBlank() -> dataSource.selectDepotLine(line.url, line.name, onResult)
                                                else -> dataSource.loadConfig(line.type, line.url, line.name, onResult)
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
    var bodyFocused by remember { mutableStateOf(false) }
    var deleteFocused by remember { mutableStateOf(false) }
    val focused = bodyFocused || deleteFocused
    val canAct = enabled && !loading
    val bringIntoView = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(8.dp)
    val itemShape = RoundedCornerShape(6.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp)
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
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 「主体」和「删除」必须是同级焦点。删除放在可点击的整行内部时，D-pad 无法进入子节点。
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .bringIntoViewRequester(bringIntoView)
                .onFocusChanged {
                    bodyFocused = it.isFocused
                    if (it.isFocused) scope.launch { bringIntoView.bringIntoView() }
                }
                .clip(itemShape)
                .background(
                    if (bodyFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
                    else Color.Transparent,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canAct,
                    onClick = onPick,
                )
                .padding(horizontal = 4.dp),
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
        }
        Spacer(Modifier.width(2.dp))
        // 独立焦点：D-pad 右移到「删除」，OK 键触发删除
        Box(
            Modifier
                .onFocusChanged { deleteFocused = it.isFocused }
                .clip(itemShape)
                .background(
                    if (deleteFocused) MaterialTheme.colorScheme.error else Color.Transparent,
                )
                .then(
                    if (deleteFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.onError, itemShape)
                    else Modifier,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = canAct,
                    onClick = onDelete,
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "删除",
                fontSize = 12.sp,
                color = when {
                    deleteFocused -> MaterialTheme.colorScheme.onError
                    canAct -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
    }
}

private fun ConfigLine.key(): String = "$type|$url|$name"
