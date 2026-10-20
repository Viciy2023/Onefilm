package com.onefilm.android.tv.compose.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.bridge.SettingsDataSource
import com.onefilm.android.tv.compose.bridge.SettingsSnapshot
import com.onefilm.android.tv.compose.design.ThemeController

@Composable
fun SettingsScreen(
    dataSource: SettingsDataSource,
    onConfigChanged: () -> Unit = {},
) {
    var snapshot by remember { mutableStateOf(dataSource.load()) }
    var editType by remember { mutableStateOf<Int?>(null) }
    var editUrl by remember { mutableStateOf("") }
    var editName by remember { mutableStateOf("") }
    var configLoading by remember { mutableStateOf(false) }
    var reloadingType by remember { mutableStateOf<Int?>(null) }
    var configError by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }
    var qrUrl by remember { mutableStateOf("") }
    var qrBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var editBlockWords by remember { mutableStateOf(false) }
    var blockWordsDraft by remember { mutableStateOf("") }

    fun refresh(change: SettingsDataSource.() -> Unit) {
        dataSource.change()
        snapshot = dataSource.load()
    }

    fun openConfig(type: Int, url: String) {
        editType = type
        editUrl = url
        editName = dataSource.configLines(type).firstOrNull { it.current }?.name.orEmpty()
        configError = null
        qrUrl = dataSource.configPushUrl()
        qrBitmap = dataSource.qrBitmap(qrUrl, 720)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium)
        Text(
            "播放、字幕、弹幕、预加载与网络参数均绑定旧版 *Setting 类",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("接口配置") {
            ConfigRow("点播接口", snapshot.configUrl, onEdit = { openConfig(0, snapshot.configUrl) }, onReload = {
                val url = snapshot.configUrl
                if (url.isNotBlank()) {
                    reloadingType = 0
                    dataSource.loadConfig(0, url) { ok, _ ->
                        reloadingType = null
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                        }
                    }
                }
            })
            ConfigRow("直播接口", snapshot.liveConfigUrl, onEdit = { openConfig(1, snapshot.liveConfigUrl) }, onReload = {
                val url = snapshot.liveConfigUrl
                if (url.isNotBlank()) {
                    reloadingType = 1
                    dataSource.loadConfig(1, url) { ok, _ ->
                        reloadingType = null
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                        }
                    }
                }
            })
            ConfigRow("壁纸接口", snapshot.wallConfigUrl, onEdit = { openConfig(2, snapshot.wallConfigUrl) }, onReload = {
                val url = snapshot.wallConfigUrl
                if (url.isNotBlank()) {
                    reloadingType = 2
                    dataSource.loadConfig(2, url) { ok, _ ->
                        reloadingType = null
                        if (ok) {
                            onConfigChanged()
                            snapshot = dataSource.load()
                        }
                    }
                }
            })
            ActionRow("DoH", snapshot.dohLabel.ifBlank { "跟随配置" }) { refresh { cycleDoh() } }
            ActionRow("扫码推送", "手机扫码打开配置页") {
                qrUrl = dataSource.configPushUrl()
                qrBitmap = dataSource.qrBitmap(qrUrl, 720)
                showQr = true
            }
            SwitchRow("隐身模式", "不写入历史与缓存", snapshot.incognito) {
                refresh { setIncognito(it) }
            }
        }

        Section("主题设置") {
            ChoiceRow(
                title = "外观主题",
                subtitle = "默认简约明亮主题",
                options = listOf(
                    "简约" to (snapshot.themeMode == 0),
                    "暗黑" to (snapshot.themeMode == 1),
                    "跟随系统" to (snapshot.themeMode == 2),
                ),
            ) { index ->
                refresh { setThemeMode(index) }
                ThemeController.apply(index)
            }
        }

        Section("播放设置") {
            ChoiceRow(
                title = "播放引擎",
                subtitle = "MPV 与 Exo 共用旧 PlayerManager",
                options = listOf("Exo" to !snapshot.engineMpv, "MPV" to snapshot.engineMpv),
            ) { index -> refresh { setEngine(index == 1) } }
            ChoiceRow(
                title = "后台播放",
                subtitle = "按 Home 后的行为",
                options = listOf(
                    "关闭" to (snapshot.background == 0),
                    "后台" to (snapshot.background == 1),
                    "PiP" to (snapshot.background == 2),
                ),
            ) { index -> refresh { setBackground(index) } }
            ChoiceRow(
                title = "缓冲区",
                subtitle = "${snapshot.bufferSeconds} 秒",
                options = listOf(1, 2, 5, 10).map {
                    "${it}s" to (snapshot.bufferSeconds == it)
                },
            ) { index -> refresh { setBufferSeconds(listOf(1, 2, 5, 10)[index]) } }
            ChoiceRow(
                title = "渲染方式",
                subtitle = "Surface / TextureView",
                options = listOf("Surface" to !snapshot.renderTexture, "Texture" to snapshot.renderTexture),
            ) { index -> refresh { setRenderTexture(index == 1) } }
            SwitchRow("隧道解码", "需要 Surface 渲染", snapshot.tunnel) { refresh { setTunnel(it) } }
        }

        Section("解码设置") {
            SwitchRow("音频直通", "HDMI / 功放透传", snapshot.audioPassThrough) {
                refresh { setAudioPassThrough(it) }
            }
            SwitchRow("音频偏软解", "软解优先使用 Audio 解码器", snapshot.audioPrefer) {
                refresh { setAudioPrefer(it) }
            }
            SwitchRow("视频偏软解", "软解优先使用 Video 解码器", snapshot.videoPrefer) {
                refresh { setVideoPrefer(it) }
            }
            ActionRow(
                title = "Dolby Vision 输出",
                value = snapshot.dvPolicyLabel,
            ) { refresh { cycleDolbyVisionPolicy() } }
            Text(
                "运行时硬解/软解切换仍在播放器「更多」菜单中即时生效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        Section("字幕设置") {
            ChoiceRow(
                title = "字幕样式",
                subtitle = "原始 / 系统 / 自定义",
                options = listOf(
                    "原始" to (snapshot.subtitleStyle == 0),
                    "系统" to (snapshot.subtitleStyle == 1),
                    "自定义" to (snapshot.subtitleStyle == 2),
                ),
            ) { index -> refresh { setSubtitleStyle(index) } }
            ChoiceRow(
                title = "字幕大小",
                subtitle = "%.1fx".format(snapshot.subtitleScale),
                options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                    "%.1fx".format(it) to (snapshot.subtitleScale == it)
                },
            ) { index -> refresh { setSubtitleScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            ChoiceRow(
                title = "字幕位置",
                subtitle = snapshot.subtitlePosition.toInt().toString(),
                options = listOf(-5f, 0f, 5f, 10f, 20f).map {
                    it.toInt().toString() to (snapshot.subtitlePosition == it)
                },
            ) { index -> refresh { setSubtitlePosition(listOf(-5f, 0f, 5f, 10f, 20f)[index]) } }
            ActionRow("字幕颜色", snapshot.subtitleColorLabel) { refresh { cycleSubtitleColor() } }
            ChoiceRow(
                title = "字幕描边",
                options = listOf(0f, 1f, 2f, 4f).map {
                    "%.0f".format(it) to (snapshot.subtitleEdgeWidth == it)
                },
            ) { index -> refresh { setSubtitleEdgeWidth(listOf(0f, 1f, 2f, 4f)[index]) } }
        }

        Section("弹幕设置") {
            SwitchRow("显示弹幕", "加载并渲染弹幕", snapshot.danmakuShow) {
                refresh { setDanmakuShow(it) }
            }
            ChoiceRow(
                title = "弹幕字号",
                options = listOf(0.8f, 1.0f, 1.2f, 1.5f).map {
                    "%.1fx".format(it) to (snapshot.danmakuScale == it)
                },
            ) { index -> refresh { setDanmakuScale(listOf(0.8f, 1.0f, 1.2f, 1.5f)[index]) } }
            ChoiceRow(
                title = "弹幕透明度",
                options = listOf(0f, 0.25f, 0.5f, 0.7f).map {
                    "${(it * 100).toInt()}%" to (snapshot.danmakuTransparency == it)
                },
            ) { index -> refresh { setDanmakuTransparency(listOf(0f, 0.25f, 0.5f, 0.7f)[index]) } }
            ChoiceRow(
                title = "弹幕速度",
                options = listOf(6000L, 8000L, 10000L).map {
                    "${it / 1000}s" to (snapshot.danmakuDurationMs == it)
                },
            ) { index -> refresh { setDanmakuDuration(listOf(6000L, 8000L, 10000L)[index]) } }
            ChoiceRow(
                title = "滚动行数",
                options = listOf(4, 8, 12).map { "$it 行" to (snapshot.danmakuLines == it) },
            ) { index -> refresh { setDanmakuLines(listOf(4, 8, 12)[index]) } }
            ActionRow("弹幕屏蔽词", snapshot.danmakuBlockWords.ifBlank { "未设置" }) {
                blockWordsDraft = snapshot.danmakuBlockWords
                editBlockWords = true
            }
        }

        Section("预加载设置") {
            SwitchRow("开启预加载", "使用旧 PlayerManager 预加载", snapshot.preloadEnabled) {
                refresh { setPreloadEnabled(it) }
            }
            SwitchRow("预加载下一集", "仅 Exo 路径生效", snapshot.preloadNext) {
                refresh { setPreloadNext(it) }
            }
            ChoiceRow(
                title = "预加载线程",
                options = (1..4).map { "$it" to (snapshot.preloadThreads == it) },
            ) { index -> refresh { setPreloadThreads(index + 1) } }
            ChoiceRow(
                title = "缓存大小",
                options = listOf(50, 100, 200, 500).map {
                    "$it MB" to (snapshot.preloadSizeMb == it)
                },
            ) { index -> refresh { setPreloadSizeMb(listOf(50, 100, 200, 500)[index]) } }
        }

        Section("网络与 UA") {
            Text(
                text = snapshot.ua.ifBlank { "默认 User-Agent" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            ActionRow("恢复默认 UA", "清空自定义 UA") { refresh { resetUa() } }
        }

        Section("关于 OneFilm") {
            InfoRow("版本", snapshot.version)
            InfoRow("配置地址", snapshot.configUrl.ifBlank { "未配置" })
            InfoRow("开源许可", "GPL-3.0 · 基于 FongMi/TV")
        }

        Spacer(Modifier.height(24.dp))
    }

    if (editBlockWords) {
        AlertDialog(
            onDismissRequest = { editBlockWords = false },
            title = { Text("弹幕屏蔽词") },
            text = {
                OutlinedTextField(
                    value = blockWordsDraft,
                    onValueChange = { blockWordsDraft = it },
                    singleLine = false,
                    label = { Text("多个关键词用逗号分隔") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dataSource.setDanmakuBlockWords(blockWordsDraft.trim())
                        snapshot = dataSource.load()
                        editBlockWords = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { editBlockWords = false }) { Text("取消") }
            },
        )
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("扫码推送配置") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "配置二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(260.dp),
                        )
                    } ?: Text("二维码生成失败")
                    Spacer(Modifier.height(10.dp))
                    Text(qrUrl, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "手机扫码后可在网页端推送点播/直播/壁纸配置",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text("关闭") }
            },
        )
    }

    val type = editType
    if (type != null) {
        AlertDialog(
            onDismissRequest = { if (!configLoading) editType = null },
            title = {
                Text(
                    when (type) {
                        0 -> "点播接口"
                        1 -> "直播接口"
                        else -> "壁纸接口"
                    },
                )
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "配置二维码",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(160.dp),
                        )
                    }
                    Text(
                        "扫码打开网页配置页",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        singleLine = true,
                        label = { Text("线路名称") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editUrl,
                        onValueChange = { editUrl = it },
                        singleLine = true,
                        label = { Text("配置 URL") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    configError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !configLoading && editUrl.isNotBlank(),
                    onClick = {
                        configLoading = true
                        dataSource.loadConfig(type, editUrl.trim(), editName.trim()) { ok, message ->
                            configLoading = false
                            if (ok) {
                                onConfigChanged()
                                snapshot = dataSource.load()
                                editType = null
                            } else {
                                configError = message
                            }
                        }
                    },
                ) { Text(if (configLoading) "保存中…" else "保存") }
            },
            dismissButton = {
                TextButton(enabled = !configLoading, onClick = { editType = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ConfigRow(
    title: String,
    value: String,
    onEdit: () -> Unit,
    onReload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        Text(
            value.ifBlank { "未配置" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .width(56.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(enabled = value.isNotBlank(), onClick = onReload),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "加载",
                style = MaterialTheme.typography.labelLarge,
                color = if (value.isNotBlank()) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "−" else "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surface),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = { onChange(it) })
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String? = null,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEachIndexed { index, (label, selected) ->
                SettingChip(label, selected) { onSelect(index) }
            }
        }
    }
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.onSurface
                    focused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.background
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ActionRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
