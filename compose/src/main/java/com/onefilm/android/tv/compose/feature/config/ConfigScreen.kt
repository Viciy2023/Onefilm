package com.onefilm.android.tv.compose.feature.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    val pushUrl = remember { dataSource.configPushUrl() }
    val qrBitmap = remember(pushUrl) {
        if (pushUrl.isBlank()) null else dataSource.qrBitmap(pushUrl, 640)
    }

    fun loadCurrent() {
        val line = dataSource.configLines(type).firstOrNull { it.current }
            ?: dataSource.configLines(type).firstOrNull()
        name = line?.name.orEmpty()
        url = line?.url.orEmpty()
        error = null
    }

    LaunchedEffect(type) { loadCurrent() }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 22.dp)) {
        Text("接口配置", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "壹影提示：第1次使用点配置点储存权限再点确定，或者手机浏览器直接访问 ${pushUrl.ifBlank { "（推送服务未启动）" }}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("点播接口", "直播接口").forEachIndexed { index, label ->
                val selected = type == index
                Text(
                    text = label,
                    color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    fontSize = 19.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.surfaceVariant,
                        )
                        .clickable { if (!loading) type = index }
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 30.dp, vertical = 24.dp),
        ) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Column(
                    Modifier.weight(0.34f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "配置二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(230.dp),
                        )
                    } ?: Text("二维码生成失败", fontSize = 16.sp)
                    Text(
                        text = "手机扫码配置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.width(30.dp))
                Column(
                    Modifier.weight(0.66f).fillMaxHeight(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "已配置线路",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 18.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = name.ifBlank { "未配置" },
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (type == 0) "点播接口" else "直播接口",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 17.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            singleLine = true,
                            label = { Text("线路名称") },
                            modifier = Modifier.weight(0.42f),
                        )
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            singleLine = true,
                            label = { Text(if (type == 0) "点播接口 URL" else "直播接口 URL") },
                            modifier = Modifier.weight(0.58f),
                        )
                    }
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, enabled = !loading) {
                    Text("取消", fontSize = 18.sp)
                }
                Spacer(Modifier.width(14.dp))
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
                        Text("保存并加载", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
