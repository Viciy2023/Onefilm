package com.onefilm.android.tv.compose.feature.lines

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource

@Composable
fun LinesScreen(
    dataSource: SettingsDataSource,
    onDone: () -> Unit,
    onConfigChanged: () -> Unit = {},
) {
    var tab by remember { mutableIntStateOf(0) }
    var lines by remember { mutableStateOf(dataSource.configLines(0)) }
    var loadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate) }

    LaunchedEffect(tab) {
        lines = dataSource.configLines(tab)
        error = null
    }

    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(760.dp)
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 30.dp, vertical = 24.dp),
        ) {
            Text("线路", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("点播线路", "直播线路", "壁纸线路").forEachIndexed { index, label ->
                    val selected = tab == index
                    Text(
                        text = label,
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceVariant,
                            )
                            .clickable { tab = index }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            if (lines.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("暂无线路，请先在配置页添加。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    lines.forEach { line ->
                        LineRow(
                            line = line,
                            loading = loadingUrl == line.url,
                            onClick = {
                                if (loadingUrl == null) {
                                    loadingUrl = line.url
                                    error = null
                                    dataSource.loadConfig(tab, line.url, line.name) { ok, message ->
                                        loadingUrl = null
                                        if (ok) {
                                            onConfigChanged()
                                            onDone()
                                        } else {
                                            error = message
                                        }
                                    }
                                }
                            },
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
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onDone) { Text("取消", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun LineRow(
    line: ConfigLine,
    loading: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (line.current) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = line.name.ifBlank { "未命名线路" },
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            line.url.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = when {
                loading -> "加载中…"
                line.current -> "当前"
                else -> "切换"
            },
            color = if (line.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            fontWeight = if (line.current) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
