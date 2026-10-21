package com.onefilm.android.tv.compose.feature.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.ServerEventBridge

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
    var error by remember { mutableStateOf<String?>(null) }
    var lines by remember { mutableStateOf(dataSource.allConfigLines()) }
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

    Dialog(
        onDismissRequest = { if (!loading) onBack() },
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
                Row(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.weight(0.33f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        qrBitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "配置二维码",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            )
                        } ?: Text("二维码生成失败", fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(
                        Modifier
                            .weight(0.67f)
                            .heightIn(max = 120.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (lines.isEmpty()) {
                            Text("暂无已配置线路", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        } else {
                            lines.forEach { line ->
                                ConfigLineRow(
                                    line = line,
                                    onPick = {
                                        type = line.type
                                        name = line.name
                                        url = line.url
                                        error = null
                                    },
                                    onDelete = {
                                        dataSource.deleteConfigLine(line.type, line.url)
                                        refreshLines()
                                        onConfigChanged()
                                    },
                                )
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
                        enabled = !loading,
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
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.CenterStart) {
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
    onPick: () -> Unit,
    onDelete: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(shape)
            .background(
                if (line.current) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            .border(
                1.dp,
                if (line.current) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.name.ifBlank { line.url },
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 6.dp),
        )
        if (line.type == 1) {
            Text("直播", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onDelete) {
            Text("删除", fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
