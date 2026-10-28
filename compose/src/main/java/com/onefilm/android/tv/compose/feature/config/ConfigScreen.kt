package com.onefilm.android.tv.compose.feature.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.ServerEventBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConfigScreen(
    dataSource: SettingsDataSource,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onConfigChanged: () -> Unit = {},
) {
    var type by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var loadingLineKey by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf(dataSource.allConfigLines()) }
    val firstLineFocus = remember { FocusRequester() }
    val pushUrl = remember { dataSource.configPushUrl() }
    val qrBitmap = remember(pushUrl) {
        if (pushUrl.isBlank()) null else dataSource.qrBitmap(pushUrl, 640)
    }

    fun refreshLines() {
        lines = dataSource.allConfigLines()
    }

    LaunchedEffect(Unit) {
        refreshLines()
        ServerEventBridge.settings.collect { pushed ->
            name = pushed.name
            url = pushed.text
            error = null
        }
    }

    // 未加载过的接口：后台探测一次单仓/多仓，用于列表标签
    LaunchedEffect(Unit) {
        dataSource.detectConfigKinds()
        refreshLines()
    }

    LaunchedEffect(lines) {
        if (lines.isNotEmpty()) {
            delay(80)
            runCatching { firstLineFocus.requestFocus() }
        }
    }

    Dialog(
        onDismissRequest = { if (!loading && loadingLineKey == null) onBack() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(560.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Text(
                    text = "壹影提示：第1次使用点配置点储存权限再点确定，或者手机浏览器直接访问 ${pushUrl.ifBlank { "（推送服务未启动）" }}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // 二维码和线路框同高；188dp 在 6 条紧凑线路时仍留有余量。
                val qrSize = 188.dp
                Row(
                    Modifier.fillMaxWidth().height(qrSize),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.width(qrSize).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "配置二维码",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } ?: Text("二维码生成失败", fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
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
                                "暂无已配置线路",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        } else {
                            Column(
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                lines.forEachIndexed { index, line ->
                                    ConfigLineRow(
                                        line = line,
                                        loading = loadingLineKey == line.key(),
                                        enabled = loadingLineKey == null && !loading,
                                        focusRequester = if (index == 0) firstLineFocus else null,
                                        onPick = {
                                            if (loadingLineKey == null && !loading) {
                                                type = line.type
                                                name = line.name
                                                url = line.url
                                                error = null
                                                loadingLineKey = line.key()
                                                dataSource.loadConfig(line.type, line.url, line.name) { ok, message ->
                                                    loadingLineKey = null
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
                                            if (loadingLineKey == null && !loading) {
                                                dataSource.deleteConfigLine(line.type, line.url)
                                                refreshLines()
                                                onConfigChanged()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CompactInput(
                        value = name,
                        hint = "线路名称",
                        onValueChange = { name = it },
                        modifier = Modifier.weight(0.34f),
                    )
                    CompactInput(
                        value = url,
                        hint = "接口 URL",
                        onValueChange = { url = it },
                        modifier = Modifier.weight(0.44f),
                    )
                    Button(
                        onClick = {
                            if (url.isBlank()) {
                                error = "接口 URL 不能为空"
                            } else {
                                loading = true
                                error = null
                                dataSource.loadConfig(type, url.trim(), name.trim()) { ok, message ->
                                    loading = false
                                    if (ok) onDone() else error = message
                                }
                            }
                        },
                        enabled = !loading && loadingLineKey == null,
                        modifier = Modifier.height(38.dp).width(106.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存并加载", fontSize = 12.sp)
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
private fun CompactInput(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) {
                    Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        },
    )
}

@Composable
private fun ConfigLineRow(
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
            .padding(horizontal = 3.dp),
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
                .padding(horizontal = 5.dp),
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
            if (line.type == 1) {
                Text("直播", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            when (line.kind) {
                2 -> {
                    Text("多仓", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                }
                1 -> {
                    Text("单仓", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
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
