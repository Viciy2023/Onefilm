package com.onefilm.android.tv.mobileui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.design.ThemeController
import com.github.catvod.utils.Prefers

private data class MobileChoice(
    val title: String,
    val options: List<String>,
    val selected: Int,
    val onSelect: (Int) -> Unit,
)

@Composable
fun MobileSettingsScreen(dataSource: SettingsDataSource) {
    var snapshot by remember { mutableStateOf(dataSource.load()) }
    var choice by remember { mutableStateOf<MobileChoice?>(null) }
    var configType by remember { mutableStateOf<Int?>(null) }
    var configDraft by remember { mutableStateOf("") }
    var configBusy by remember { mutableStateOf(false) }
    var configError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        snapshot = dataSource.load()
    }

    fun choose(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        choice = MobileChoice(title, options, selected) { index ->
            onSelect(index)
            choice = null
            refresh()
        }
    }

    val liveUrl = Prefers.getString("compose_live_url").orEmpty()
        .ifBlank { snapshot.liveConfigUrl }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { MobileSectionTitle("接口配置") }
        item {
            MobileSettingRow("点播接口", snapshot.configUrl) {
                configType = 0
                configDraft = snapshot.configUrl
                configError = null
            }
        }
        item {
            MobileSettingRow("直播接口", liveUrl) {
                configType = 1
                configDraft = liveUrl
                configError = null
            }
        }
        item {
            MobileSettingRow("壁纸接口", snapshot.wallConfigUrl) {
                configType = 2
                configDraft = snapshot.wallConfigUrl
                configError = null
            }
        }

        item { MobileSectionTitle("外观") }
        item {
            MobileSettingRow(
                "外观主题",
                listOf("简约", "暗黑", "跟随系统").getOrElse(snapshot.themeMode) { "简约" },
            ) {
                choose("外观主题", listOf("简约", "暗黑", "跟随系统"), snapshot.themeMode) { index ->
                    dataSource.setThemeMode(index)
                    ThemeController.apply(index)
                }
            }
        }
        item {
            MobileSettingRow("壁纸切换", snapshot.wallpaperLabel) {
                dataSource.cycleWallpaper()
                refresh()
            }
        }
        item {
            MobileSettingRow("DoH", snapshot.dohLabel.ifBlank { "跟随配置" }) {
                dataSource.cycleDoh()
                refresh()
            }
        }

        item { MobileSectionTitle("播放") }
        item {
            MobileSettingRow("播放超时", "${snapshot.playTimeoutSeconds} 秒") {
                dataSource.cyclePlayTimeout()
                refresh()
            }
        }
        item {
            MobileSettingRow("播放引擎", if (snapshot.engineMpv) "MPV" else "Exo") {
                choose("播放引擎", listOf("Exo", "MPV"), if (snapshot.engineMpv) 1 else 0) {
                    dataSource.setEngine(it == 1)
                }
            }
        }
        item {
            MobileSettingRow("缓冲区", "${snapshot.bufferSeconds} 秒") {
                choose("缓冲区", listOf("1 秒", "2 秒", "5 秒", "10 秒"), when (snapshot.bufferSeconds) {
                    1 -> 0; 2 -> 1; 5 -> 2; else -> 3
                }) { dataSource.setBufferSeconds(listOf(1, 2, 5, 10)[it]) }
            }
        }
        item {
            MobileSettingRow("渲染方式", if (snapshot.renderTexture) "Texture" else "Surface") {
                choose("渲染方式", listOf("Surface", "Texture"), if (snapshot.renderTexture) 1 else 0) {
                    dataSource.setRenderTexture(it == 1)
                }
            }
        }
        item {
            MobileSwitchRow("隧道解码", "需要 Surface 渲染", snapshot.tunnel) {
                dataSource.setTunnel(it); refresh()
            }
        }
        item {
            MobileSwitchRow("音频直通", "HDMI / 功放透传", snapshot.audioPassThrough) {
                dataSource.setAudioPassThrough(it); refresh()
            }
        }

        item { MobileSectionTitle("字幕与弹幕") }
        item {
            MobileSettingRow("字幕位置", snapshot.subtitlePosition.toInt().toString()) {
                choose("字幕位置", listOf("-5", "0", "5", "10", "20"), listOf(-5f, 0f, 5f, 10f, 20f).indexOf(snapshot.subtitlePosition).coerceAtLeast(0)) {
                    dataSource.setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[it])
                }
            }
        }
        item { MobileSettingRow("字幕颜色", snapshot.subtitleColorLabel) { dataSource.cycleSubtitleColor(); refresh() } }
        item { MobileSwitchRow("显示弹幕", "加载并渲染弹幕", snapshot.danmakuShow) { dataSource.setDanmakuShow(it); refresh() } }
        item { MobileSwitchRow("隐身模式", "不写入历史与缓存", snapshot.incognito) { dataSource.setIncognito(it); refresh() } }

        item { MobileSectionTitle("预加载与缓存") }
        item { MobileSwitchRow("开启预加载", "使用 PlayerManager 预加载", snapshot.preloadEnabled) { dataSource.setPreloadEnabled(it); refresh() } }
        item { MobileSwitchRow("预加载下一集", "Exo 路径生效", snapshot.preloadNext) { dataSource.setPreloadNext(it); refresh() } }
        item {
            MobileSettingRow("预加载线程", "${snapshot.preloadThreads}") {
                choose("预加载线程", listOf("1", "2", "3", "4"), snapshot.preloadThreads - 1) {
                    dataSource.setPreloadThreads(it + 1)
                }
            }
        }
        item {
            MobileSettingRow("缓存天数", "${snapshot.cacheDays} 天") {
                choose("缓存天数", listOf("7 天", "30 天", "60 天", "90 天"), listOf(7, 30, 60, 90).indexOf(snapshot.cacheDays).coerceAtLeast(0)) {
                    dataSource.setCacheDays(listOf(7, 30, 60, 90)[it])
                }
            }
        }
        item { MobileSettingRow("清除缓存", snapshot.cacheSizeLabel) { dataSource.clearCache(); refresh() } }

        item { MobileSectionTitle("更新") }
        item {
            MobileSwitchRow("强制升级", "检测到新版后自动下载并强制更新", snapshot.forceUpdate) {
                dataSource.setForceUpdate(it); refresh()
            }
        }

        item { MobileSectionTitle("关于") }
        item { MobileSettingRow("版本", "${snapshot.version} · GPL-3.0") { } }
    }

    choice?.let { current ->
        AlertDialog(
            onDismissRequest = { choice = null },
            title = { Text(current.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    current.options.forEachIndexed { index, option ->
                        TextButton(onClick = { current.onSelect(index) }, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                option,
                                modifier = Modifier.fillMaxWidth(),
                                fontWeight = if (index == current.selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }

    configType?.let { type ->
        AlertDialog(
            onDismissRequest = { if (!configBusy) configType = null },
            title = { Text(if (type == 0) "点播接口" else if (type == 1) "直播接口" else "壁纸接口") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = configDraft,
                        onValueChange = { configDraft = it },
                        singleLine = true,
                        placeholder = { Text("粘贴接口地址") },
                        enabled = !configBusy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!configError.isNullOrBlank()) {
                        Text(configError ?: "", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !configBusy,
                    onClick = {
                        if (configDraft.isBlank()) {
                            configError = "请输入接口地址"
                        } else {
                            configBusy = true
                            configError = null
                            if (type == 1) {
                                Prefers.put("compose_live_url", configDraft.trim())
                            }
                            dataSource.loadConfig(type, configDraft.trim()) { ok, message ->
                                configBusy = false
                                if (ok) {
                                    configType = null
                                    refresh()
                                } else {
                                    configError = message.ifBlank { "加载失败" }
                                }
                            }
                        }
                    },
                ) { Text(if (configBusy) "加载中…" else "加载") }
            },
            dismissButton = {
                TextButton(onClick = { configType = null }, enabled = !configBusy) { Text("取消") }
            },
        )
    }
}

@Composable
private fun MobileSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun MobileSettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            value.ifBlank { "未设置" },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MobileSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(
                subtitle,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
