package com.onefilm.android.tv.compose.feature.netdisk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.NetdiskDataSource
import com.onefilm.android.tv.compose.bridge.NetdiskDrive
import com.onefilm.android.tv.compose.bridge.NetdiskEntry
import com.onefilm.android.tv.compose.bridge.NetdiskPlayback
import com.onefilm.android.tv.compose.navigation.TvBrandRow
import org.json.JSONObject
import kotlinx.coroutines.launch

@Composable
fun NetdiskScreen(
    dataSource: NetdiskDataSource,
    onPlay: (NetdiskPlayback, NetdiskEntry) -> Unit,
) {
    var drives by remember { mutableStateOf(dataSource.listDrives()) }
    var currentDrive by remember { mutableStateOf<NetdiskDrive?>(null) }
    var currentPath by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NetdiskEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<NetdiskDrive?>(null) }
    var adding by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val drive = currentDrive
    LaunchedEffect(drive?.id, currentPath) {
        if (drive == null) return@LaunchedEffect
        page = 1
        loading = true
        error = null
        runCatching { dataSource.listEntries(drive, currentPath, 1) }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: "读取网盘失败" }
        loading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
        TvBrandRow(
            compact = true,
            horizontalPadding = 0.dp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (drive == null) "网盘" else drive.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            if (drive == null) {
                Button(onClick = { adding = true }) { Text("添加网盘") }
            } else {
                TextButton(onClick = {
                    error = null
                    if (currentPath.isBlank()) currentDrive = null
                    else currentPath = currentPath.substringBeforeLast('/', "")
                }) { Text("返回") }
            }
        }
        Text(
            text = if (drive == null) "添加 WebDAV 或 Alist 网页，浏览网盘中的影片。"
            else currentPath.ifBlank { "/" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        when {
            drive == null -> DriveList(
                drives = drives,
                onOpen = {
                    currentDrive = it
                    currentPath = ""
                    error = null
                },
                onEdit = { editing = it },
                onDelete = {
                    dataSource.deleteDrive(it.id)
                    drives = dataSource.listDrives()
                },
            )
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                com.onefilm.android.tv.compose.design.component.AppLoadingState()
            }
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("此目录为空", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(entries, key = { "${it.path}:${it.name}" }) { entry ->
                    NetdiskEntryCard(entry) {
                        if (entry.isDirectory) {
                            currentPath = entry.path
                        } else {
                            loading = true
                            error = null
                            scope.launch {
                                runCatching { dataSource.resolve(drive, entry) }
                                    .onSuccess { onPlay(it, entry) }
                                    .onFailure { error = it.message ?: "解析文件失败" }
                                loading = false
                            }
                        }
                    }
                }
                if (drive.type == 2 && entries.size >= 100) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = {
                                    if (!loadingMore) {
                                        loadingMore = true
                                        scope.launch {
                                            runCatching { dataSource.listEntries(drive, currentPath, page + 1) }
                                                .onSuccess {
                                                    entries = entries + it
                                                    page += 1
                                                }
                                                .onFailure { error = it.message ?: "加载更多失败" }
                                            loadingMore = false
                                        }
                                    }
                                },
                                enabled = !loadingMore,
                            ) { Text(if (loadingMore) "加载中…" else "加载更多") }
                        }
                    }
                }
            }
        }
    }

    if (adding) {
        DriveDialog(
            drive = null,
            onDismiss = { adding = false },
            onSave = {
                dataSource.saveDrive(it)
                drives = dataSource.listDrives()
                adding = false
            },
        )
    }
    editing?.let { item ->
        DriveDialog(
            drive = item,
            onDismiss = { editing = null },
            onSave = {
                dataSource.saveDrive(it)
                drives = dataSource.listDrives()
                editing = null
            },
        )
    }
}

@Composable
private fun DriveList(
    drives: List<NetdiskDrive>,
    onOpen: (NetdiskDrive) -> Unit,
    onEdit: (NetdiskDrive) -> Unit,
    onDelete: (NetdiskDrive) -> Unit,
) {
    if (drives.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("还没有网盘配置，点击右上角「添加网盘」。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        drives.forEach { drive ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(drive) }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text(if (drive.type == 1) "DAV" else "AL", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(drive.name.ifBlank { "未命名网盘" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (drive.type == 1) "WebDAV" else "Alist 网页",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                TextButton(onClick = { onEdit(drive) }) { Text("编辑") }
                TextButton(onClick = { onDelete(drive) }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun NetdiskEntryCard(entry: NetdiskEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                RoundedCornerShape(20.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (entry.isDirectory) "📁" else "🎬", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            text = entry.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DriveDialog(
    drive: NetdiskDrive?,
    onDismiss: () -> Unit,
    onSave: (NetdiskDrive) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var type by remember(drive?.id) { mutableStateOf(drive?.type ?: 1) }
    var name by remember(drive?.id) { mutableStateOf(drive?.name.orEmpty()) }
    var url by remember(drive?.id) { mutableStateOf("") }
    var initPath by remember(drive?.id) { mutableStateOf("") }
    var username by remember(drive?.id) { mutableStateOf("") }
    var password by remember(drive?.id) { mutableStateOf("") }
    var localUri by remember(drive?.id) { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            localUri = uri.toString()
            if (name.isBlank()) name = uri.lastPathSegment.orEmpty()
        }
    }

    androidx.compose.runtime.LaunchedEffect(drive?.id) {
        if (drive != null) {
            runCatching {
                JSONObject(drive.configJson).let { json ->
                    url = json.optString("url")
                    initPath = json.optString("initPath")
                    username = json.optString("username")
                    password = json.optString("password")
                    localUri = json.optString("uri")
                }
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.56f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "本地目录", 1 to "WebDAV", 2 to "Alist 网页").forEach { (value, label) ->
                        val selected = type == value
                        Text(
                            text = label,
                            color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { type = value; error = null }
                                .padding(horizontal = 13.dp, vertical = 5.dp),
                        )
                    }
                }
                FieldRow("空间名称") {
                    CompactField(name, "例如：家庭 NAS", { name = it })
                }
                when (type) {
                    0 -> FieldRow("本地目录") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = localUri.ifBlank { "未选择目录" },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(10.dp))
                            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text("选择目录") }
                        }
                    }
                    1 -> {
                        FieldRow("WebDAV 地址") { CompactField(url, "https://example.com/dav/", { url = it }) }
                        FieldRow("初始路径") { CompactField(initPath, "可选，例如 media", { initPath = it }) }
                        FieldRow("用户名") { CompactField(username, "可选", { username = it }) }
                        FieldRow("密码") { CompactField(password, "可选", { password = it }) }
                    }
                    else -> {
                        FieldRow("Alist 地址") { CompactField(url, "https://alist.example.com/", { url = it }) }
                        FieldRow("初始路径") { CompactField(initPath, "可选，例如 media", { initPath = it }) }
                        FieldRow("密码") { CompactField(password, "可选", { password = it }) }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    OutlinedButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = {
                        if (name.isBlank()) {
                            error = "空间名称不能为空"
                        } else if (type == 0 && localUri.isBlank()) {
                            error = "请选择本地目录"
                        } else if (type != 0 && url.isBlank()) {
                            error = "网盘地址不能为空"
                        } else {
                            val json = JSONObject().apply {
                                when (type) {
                                    0 -> put("uri", localUri)
                                    1 -> {
                                        put("url", url.trim().trimEnd('/') + "/")
                                        put("initPath", initPath.trim().trim('/'))
                                        put("username", username.trim())
                                        put("password", password)
                                    }
                                    else -> {
                                        put("url", url.trim().trimEnd('/') + "/")
                                        put("initPath", initPath.trim().trim('/'))
                                        put("password", password)
                                    }
                                }
                            }
                            onSave(
                                NetdiskDrive(
                                    id = drive?.id ?: 0,
                                    name = name.trim(),
                                    type = type,
                                    configJson = json.toString(),
                                ),
                            )
                        }
                    }) { Text("保存") }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(width = 120.dp, height = 40.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun CompactField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
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