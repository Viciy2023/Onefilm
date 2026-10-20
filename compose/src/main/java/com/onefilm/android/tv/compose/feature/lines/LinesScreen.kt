package com.onefilm.android.tv.compose.feature.lines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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

    fun refresh() {
        lines = dataSource.configLines(tab)
    }

    LaunchedEffect(tab) {
        error = null
        refresh()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        Text("线路", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "选择接口线路后会加载对应配置。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(10.dp))
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
                        .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { tab = index }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        if (lines.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("暂无线路，请先在配置页添加接口。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lines, key = { "${tab}:${it.url}" }) { line ->
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
                                        refresh()
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
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun LineRow(
    line: ConfigLine,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surface,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = line.name.ifBlank { line.url },
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = line.url,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = when {
                loading -> "加载中…"
                line.current -> "当前"
                else -> "切换"
            },
            color = if (line.current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            fontWeight = if (line.current) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
