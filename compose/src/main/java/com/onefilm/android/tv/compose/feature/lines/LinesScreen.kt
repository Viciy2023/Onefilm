package com.onefilm.android.tv.compose.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onefilm.android.tv.compose.bridge.ConfigLine
import com.onefilm.android.tv.compose.bridge.SettingsDataSource

@Composable
fun LinesScreen(
    dataSource: SettingsDataSource,
    onDone: () -> Unit,
    onConfigChanged: () -> Unit = {},
) {
    var lines by remember { mutableStateOf(dataSource.allConfigLines()) }
    var loadingUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        lines = dataSource.allConfigLines()
    }

    LaunchedEffect(Unit) { refresh() }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(460.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                if (lines.isEmpty()) {
                    Text(
                        text = "暂无线路，请先在配置弹窗中添加。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lines.forEach { line ->
                            LineRow(
                                line = line,
                                loading = loadingUrl == line.url,
                                onPick = {
                                    if (loadingUrl == null) {
                                        loadingUrl = line.url
                                        error = null
                                        dataSource.loadConfig(line.type, line.url, line.name) { ok, message ->
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
                                onDelete = {
                                    dataSource.deleteConfigLine(line.type, line.url)
                                    refresh()
                                    onConfigChanged()
                                },
                            )
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
            .padding(horizontal = 10.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = line.name.ifBlank { line.url },
            fontSize = 19.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).clickable(onClick = onPick).padding(vertical = 7.dp),
        )
        if (line.current) {
            Text("当前", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
        }
        if (line.type == 1) {
            Text("直播", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.width(6.dp))
        }
        TextButton(onClick = onDelete) {
            Text(
                text = if (loading) "加载中" else "删除",
                color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
            )
        }
    }
}
