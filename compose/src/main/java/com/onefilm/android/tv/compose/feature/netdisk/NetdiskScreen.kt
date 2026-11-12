package com.onefilm.android.tv.compose.feature.netdisk

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.NetdiskDataSource
import com.onefilm.android.tv.compose.bridge.NetdiskDrive
import com.onefilm.android.tv.compose.bridge.NetdiskEntry
import com.onefilm.android.tv.compose.bridge.NetdiskPlayback
import com.onefilm.android.tv.compose.design.component.TvTagButton
import org.json.JSONObject
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 弹窗里左侧标题列的宽度，标题与右侧输入框共用同一列宽。 */
private val FieldLabelWidth = 110.dp

/** 网盘列表行高：与侧栏导航按钮一致（32dp）。 */
private val NetdiskRowHeight = 32.dp

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
    // 排序：默认「文件夹优先 + 名称升序」，与资源管理器一致。
    var sortKey by remember { mutableIntStateOf(dataSource.sortKey()) }
    var sortAscending by remember { mutableStateOf(dataSource.sortAscending()) }
    val shown = remember(entries, sortKey, sortAscending) {
        sortNetdiskEntries(entries, sortKey, sortAscending)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val drive = currentDrive
    LaunchedEffect(drive?.id, currentPath) {
        if (drive == null) return@LaunchedEffect
        page = 1
        loading = true
        error = null
        runCatching { dataSource.listEntries(drive, currentPath, 1) }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: context.getString(R.string.netdisk_read_failed) }
        loading = false
    }

    Column(
        Modifier.fillMaxSize().padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (drive == null) {
                TvTagButton(text = stringResource(R.string.netdisk_add)) { adding = true }
            } else {
                TvTagButton(text = stringResource(R.string.common_back)) {
                    error = null
                    if (currentPath.isBlank()) currentDrive = null
                    else currentPath = currentPath.substringBeforeLast('/', "")
                }
                Spacer(Modifier.weight(1f))
                SortControls(
                    key = sortKey,
                    ascending = sortAscending,
                    onKeyChange = {
                        sortKey = it
                        dataSource.setSortKey(it)
                    },
                    onToggleOrder = {
                        sortAscending = !sortAscending
                        dataSource.setSortAscending(sortAscending)
                    },
                )
            }
        }
        Text(
            text = if (drive == null) stringResource(R.string.netdisk_add_hint)
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
            shown.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.netdisk_empty_dir), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown, key = { "${it.path}:${it.name}" }) { entry ->
                    NetdiskEntryRow(entry) {
                        if (entry.isDirectory) {
                            currentPath = entry.path
                        } else {
                            loading = true
                            error = null
                            scope.launch {
                                runCatching { dataSource.resolve(drive, entry) }
                                    .onSuccess { onPlay(it, entry) }
                                    .onFailure { error = it.message ?: context.getString(R.string.netdisk_parse_failed) }
                                loading = false
                            }
                        }
                    }
                }
                if (drive.type == 2 && entries.size >= 100) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            TvTagButton(
                                text = if (loadingMore) stringResource(R.string.settings_loading) else stringResource(R.string.netdisk_load_more),
                                enabled = !loadingMore,
                                onClick = {
                                    if (!loadingMore) {
                                        loadingMore = true
                                        scope.launch {
                                            runCatching { dataSource.listEntries(drive, currentPath, page + 1) }
                                                .onSuccess {
                                                    entries = entries + it
                                                    page += 1
                                                }
                                                .onFailure { error = it.message ?: context.getString(R.string.netdisk_load_more_failed) }
                                            loadingMore = false
                                        }
                                    }
                                },
                            )
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
private fun SortControls(
    key: Int,
    ascending: Boolean,
    onKeyChange: (Int) -> Unit,
    onToggleOrder: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            0 to stringResource(R.string.netdisk_sort_name),
            1 to stringResource(R.string.netdisk_sort_date),
            2 to stringResource(R.string.netdisk_sort_type),
            3 to stringResource(R.string.netdisk_sort_size),
        ).forEach { (value, label) ->
            TvTagButton(text = label, selected = key == value) { onKeyChange(value) }
        }
        TvTagButton(text = if (ascending) "↑" else "↓", onClick = onToggleOrder)
    }
}

/** 文件夹永远排在最前，其余按所选字段排序（与桌面资源管理器一致）。 */
private fun sortNetdiskEntries(
    items: List<NetdiskEntry>,
    key: Int,
    ascending: Boolean,
): List<NetdiskEntry> {
    val byKey: Comparator<NetdiskEntry> = when (key) {
        1 -> compareBy<NetdiskEntry> { it.modified }.thenBy { it.name.lowercase() }
        2 -> compareBy<NetdiskEntry> { it.name.substringAfterLast('.', "").lowercase() }
            .thenBy { it.name.lowercase() }
        3 -> compareBy<NetdiskEntry> { it.size }.thenBy { it.name.lowercase() }
        else -> compareBy { it.name.lowercase() }
    }
    val ordered = if (ascending) byKey else byKey.reversed()
    return items.sortedWith(
        compareBy<NetdiskEntry> { if (it.isDirectory) 0 else 1 }.then(ordered),
    )
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
            Text(stringResource(R.string.netdisk_no_config), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        drives.forEach { drive ->
            // 主体、编辑、删除是三个同级焦点：遥控器可以直接移到按钮上确认。
            var bodyFocused by remember(drive.id) { mutableStateOf(false) }
            val url = remember(drive.configJson) {
                runCatching { JSONObject(drive.configJson).optString("url") }.getOrDefault("")
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(NetdiskRowHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .border(
                        if (bodyFocused) 2.dp else 1.dp,
                        if (bodyFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp),
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { bodyFocused = it.isFocused }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpen(drive) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (drive.type == 1) stringResource(R.string.netdisk_type_webdav)
                        else stringResource(R.string.netdisk_type_alist),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = drive.name.ifBlank { stringResource(R.string.netdisk_unnamed) },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(10.dp))
                    // 地址过长时省略显示，不挤掉右侧按钮。
                    Text(
                        text = url,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                TvTagButton(text = stringResource(R.string.netdisk_edit), onClick = { onEdit(drive) })
                Spacer(Modifier.width(6.dp))
                TvTagButton(text = stringResource(R.string.common_delete), onClick = { onDelete(drive) })
                Spacer(Modifier.width(6.dp))
            }
        }
    }
}

/** 资源管理器式列表行：小图标 + 名称 + 修改时间 + 类型 + 大小。 */
@Composable
private fun NetdiskEntryRow(entry: NetdiskEntry, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(NetdiskRowHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(10.dp),
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Movie,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = entry.name,
            fontSize = 13.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = if (entry.modified > 0L) rememberModified(entry.modified) else "—",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(132.dp),
        )
        Text(
            text = netdiskTypeLabel(entry),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = if (entry.isDirectory) "" else formatNetdiskSize(entry.size),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(92.dp),
        )
    }
}

@Composable
private fun rememberModified(millis: Long): String {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    return remember(millis) { formatter.format(Date(millis)) }
}

@Composable
private fun netdiskTypeLabel(entry: NetdiskEntry): String {
    if (entry.isDirectory) return stringResource(R.string.netdisk_type_folder)
    val ext = entry.name.substringAfterLast('.', "")
    return if (ext.isBlank() || ext == entry.name) "—" else ext.uppercase(Locale.getDefault())
}

private fun formatNetdiskSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format(Locale.US, "%.0f KB", kb)
        kb < 1024 * 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024)
        else -> String.format(Locale.US, "%.2f GB", kb / 1024 / 1024)
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
    var token by remember(drive?.id) { mutableStateOf("") }
    var localUri by remember(drive?.id) { mutableStateOf("") }
    var showFolderPicker by remember(drive?.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(drive?.id) {
        if (drive != null) {
            runCatching {
                JSONObject(drive.configJson).let { json ->
                    url = json.optString("url")
                    initPath = json.optString("initPath")
                    username = json.optString("username")
                    password = json.optString("password")
                    token = json.optString("token")
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
                // 类型选择：与侧栏导航按钮同一套样式（32dp、10dp 圆角、聚焦高亮），
                // 左边缘与下面各行的输入框列对齐。
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(FieldLabelWidth))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0 to stringResource(R.string.netdisk_local_dir),
                            1 to stringResource(R.string.netdisk_type_webdav),
                            2 to stringResource(R.string.netdisk_type_alist),
                        ).forEach { (value, label) ->
                            TvTagButton(
                                text = label,
                                selected = type == value,
                                onClick = { type = value; error = null },
                            )
                        }
                    }
                }
                FieldRow(stringResource(R.string.netdisk_space_name)) {
                    SettingsField(name, stringResource(R.string.netdisk_name_hint), { name = it })
                }
                when (type) {
                    0 -> FieldRow(stringResource(R.string.netdisk_local_dir)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = localUri.ifBlank { stringResource(R.string.netdisk_no_dir) },
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.size(10.dp))
                            TvTagButton(text = stringResource(R.string.netdisk_select_dir), onClick = { showFolderPicker = true })
                        }
                    }
                    1 -> {
                        FieldRow(stringResource(R.string.netdisk_webdav_url)) { SettingsField(url, "https://example.com/dav/", { url = it }) }
                        FieldRow(stringResource(R.string.netdisk_init_path)) { SettingsField(initPath, stringResource(R.string.netdisk_init_path_hint), { initPath = it }) }
                        FieldRow(stringResource(R.string.netdisk_username)) { SettingsField(username, stringResource(R.string.netdisk_optional), { username = it }) }
                        FieldRow(stringResource(R.string.netdisk_password)) { SettingsField(password, stringResource(R.string.netdisk_optional), { password = it }) }
                    }
                    else -> {
                        FieldRow(stringResource(R.string.netdisk_alist_url)) { SettingsField(url, "https://alist.example.com/", { url = it }) }
                        FieldRow(stringResource(R.string.netdisk_init_path)) { SettingsField(initPath, stringResource(R.string.netdisk_init_path_hint), { initPath = it }) }
                        FieldRow(stringResource(R.string.netdisk_password)) { SettingsField(password, stringResource(R.string.netdisk_optional), { password = it }) }
                        FieldRow(stringResource(R.string.netdisk_alist_token)) { SettingsField(token, stringResource(R.string.netdisk_alist_token_hint), { token = it }) }
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TvTagButton(text = stringResource(R.string.settings_cancel), onClick = onDismiss)
                    Spacer(Modifier.size(12.dp))
                    TvTagButton(text = stringResource(R.string.settings_save), onClick = {
                        if (name.isBlank()) {
                            error = context.getString(R.string.netdisk_name_required)
                        } else if (type == 0 && localUri.isBlank()) {
                            error = context.getString(R.string.netdisk_dir_required)
                        } else if (type != 0 && url.isBlank()) {
                            error = context.getString(R.string.netdisk_url_required)
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
                                        put("token", token.trim())
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
                    })
                }
            }
        }
    }

    if (showFolderPicker) {
        LocalFolderPicker(
            initial = localUri,
            onDismiss = { showFolderPicker = false },
            onPick = { path ->
                localUri = if (path.startsWith("file://")) path else "file://$path"
                if (name.isBlank()) name = File(path).name
                showFolderPicker = false
            },
        )
    }
}

@Composable
private fun LocalFolderPicker(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val startDir = remember(initial) {
        val raw = initial.removePrefix("file://")
        val candidate = if (raw.startsWith("/")) File(raw) else null
        if (candidate != null && candidate.isDirectory) candidate
        else android.os.Environment.getExternalStorageDirectory() ?: File("/storage")
    }
    var dir by remember { mutableStateOf(startDir) }
    val children = remember(dir) {
        runCatching {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.5f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.netdisk_select_dir), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    dir.absolutePath,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TvTagButton(
                        text = stringResource(R.string.netdisk_parent_dir),
                        onClick = { dir.parentFile?.let { parent -> dir = parent } },
                    )
                    TvTagButton(
                        text = stringResource(R.string.netdisk_use_dir),
                        selected = true,
                        onClick = { onPick(dir.absolutePath) },
                    )
                    TvTagButton(text = stringResource(R.string.settings_cancel), onClick = onDismiss)
                }
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(children) { file ->
                        TvTagButton(
                            text = file.name,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { dir = file },
                        )
                    }
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
        // 标题与右侧输入框垂直居中对齐，避免字号基线差异造成"标题偏高"。
        Box(
            Modifier.width(FieldLabelWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * 输入框：与设置面板的输入项保持同一套样式
 * （34dp 最小高度、10dp 圆角、surfaceVariant 底色、聚焦 2dp 主色描边）。
 */
@Composable
private fun SettingsField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .onFocusChanged { focused = it.hasFocus }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            ),
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            hint,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )
    }
}