package com.onefilm.android.tv.compose.feature.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.SettingsDataSource

@Composable
fun ConfigScreen(
    dataSource: SettingsDataSource,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var type by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val qrUrl = remember { dataSource.configPushUrl() }
    val qrBitmap = remember(qrUrl) {
        if (qrUrl.isBlank()) null else dataSource.qrBitmap(qrUrl, 640)
    }

    fun loadCurrent() {
        val line = dataSource.configLines(type).firstOrNull { it.current }
            ?: dataSource.configLines(type).firstOrNull()
        name = line?.name.orEmpty()
        url = line?.url.orEmpty()
        error = null
    }

    LaunchedEffect(type) { loadCurrent() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "接口配置",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "扫码打开网页配置页，或手动填写线路名称与接口地址。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("点播接口", "直播接口").forEachIndexed { index, label ->
                val selected = type == index
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { if (!loading) type = index }
                        .clipPill(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth(0.72f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                qrBitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "配置二维码",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(240.dp),
                    )
                } ?: Text("二维码生成失败")
                Text(
                    text = if (qrUrl.isBlank()) "推送服务未启动" else "扫码打开网页配置页",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(18.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("线路名称") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    label = { Text(if (type == 0) "点播接口 URL" else "直播接口 URL") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(onClick = onBack, enabled = !loading) { Text("取消") }
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
                    ) {
                        if (loading) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存并加载")
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun Modifier.clipPill(color: androidx.compose.ui.graphics.Color): Modifier =
    this.then(
        Modifier
            .background(color, RoundedCornerShape(percent = 50)),
    )
