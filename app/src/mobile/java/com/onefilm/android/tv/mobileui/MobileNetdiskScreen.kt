package com.onefilm.android.tv.mobileui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.NetdiskDataSource
import com.onefilm.android.tv.compose.bridge.NetdiskDrive
import com.onefilm.android.tv.compose.bridge.NetdiskEntry
import com.onefilm.android.tv.compose.bridge.NetdiskPlayback
import com.onefilm.android.tv.compose.bridge.NetdiskSort
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 手机端网盘（继续观看 → 网盘）。触控优先的列表布局，与 TV 的 NetdiskScreen 完全独立，
 * 只共用 NetdiskDataSource 与 NetdiskSort 这类业务逻辑。
 */
@Composable
fun MobileNetdiskScreen(
    dataSource: NetdiskDataSource,
    onPlay: (NetdiskPlayback, NetdiskEntry) -> Unit,
) {
    var drives by remember { mutableStateOf(dataSource.listDrives()) }
    var drive by remember { mutableStateOf<NetdiskDrive?>(null) }
    var path by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<NetdiskEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableIntStateOf(1) }
    var loadingMore by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<NetdiskDrive?>(null) }
    var deleting by remember { mutableStateOf<NetdiskDrive?>(null) }
    var sortKey by remember { mutableIntStateOf(dataSource.sortKey()) }
    var sortAscending by remember { mutableStateOf(dataSource.sortAscending()) }
    val scope = rememberCoroutineScope()
    val emptyText = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_empty_dir)
    val readFailed = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_read_failed)
    val parseFailed = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_parse_failed)

    LaunchedEffect(drive?.id, path) {
        val current = drive ?: return@LaunchedEffect
        loading = true
        error = null
        page = 1
        runCatching { dataSource.listEntries(current, path, 1) }
            .onSuccess { entries = it }
            .onFailure { error = it.message ?: readFailed }
        loading = false
    }

    val shown = remember(entries, sortKey, sortAscending) {
        NetdiskSort.sorted(entries, sortKey, sortAscending)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (drive == null) {
                Text(
                    text = stringResource(com.onefilm.android.tv.compose.R.string.nav_netdisk),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { adding = true }) {
                    Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_add))
                }
            } else {
                TextButton(onClick = {
                    error = null
                    if (path.isBlank()) drive = null else path = path.substringBeforeLast('/', "")
                }) {
                    Text(stringResource(com.onefilm.android.tv.compose.R.string.common_back))
                }
                Text(
                    text = path.ifBlank { "/" },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { adding = true }) {
                    Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_add))
                }
            }
        }

        if (drive != null) {
            SortRow(
                key = sortKey,
                ascending = sortAscending,
                onKeyChange = { sortKey = it; dataSource.setSortKey(it) },
                onToggle = { sortAscending = !sortAscending; dataSource.setSortAscending(sortAscending) },
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        }

        // 用局部 val 做空安全：返回上一级时 drive 会变 null，LazyColumn 内容里不能再写 drive!!。
        val activeDrive = drive
        when {
            activeDrive == null -> if (drives.isEmpty()) {
                MobileCentered(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_no_config))
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(drives, key = { "drive-${it.id}" }) { item ->
                        DriveRow(
                            drive = item,
                            onOpen = {
                                drive = item
                                path = ""
                                error = null
                            },
                            onEdit = { editing = item },
                            onDelete = { deleting = item },
                        )
                    }
                }
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown.isEmpty() -> MobileCentered(emptyText)
            else -> LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(shown, key = { "${it.path}:${it.name}" }) { entry ->
                    EntryRow(entry) {
                        if (entry.isDirectory) {
                            path = entry.path
                        } else {
                            loading = true
                            error = null
                            scope.launch {
                                runCatching { dataSource.resolve(activeDrive, entry) }
                                    .onSuccess { onPlay(it, entry) }
                                    .onFailure { error = it.message ?: parseFailed }
                                loading = false
                            }
                        }
                    }
                }
                if (activeDrive.type == 2 && entries.size >= 100) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            TextButton(
                                enabled = !loadingMore,
                                onClick = {
                                    loadingMore = true
                                    scope.launch {
                                        runCatching { dataSource.listEntries(activeDrive, path, page + 1) }
                                            .onSuccess {
                                                entries = entries + it
                                                page += 1
                                            }
                                            .onFailure { error = it.message ?: readFailed }
                                        loadingMore = false
                                    }
                                },
                            ) {
                                Text(
                                    stringResource(
                                        if (loadingMore) com.onefilm.android.tv.compose.R.string.settings_loading
                                        else com.onefilm.android.tv.compose.R.string.netdisk_load_more,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        MobileDriveDialog(
            drive = editing,
            onDismiss = { adding = false; editing = null },
            onSave = {
                dataSource.saveDrive(it)
                drives = dataSource.listDrives()
                adding = false
                editing = null
            },
        )
    }

    deleting?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_edit)) },
            text = { Text(item.name.ifBlank { stringResource(com.onefilm.android.tv.compose.R.string.netdisk_unnamed) }) },
            confirmButton = {
                TextButton(onClick = {
                    dataSource.deleteDrive(item.id)
                    drives = dataSource.listDrives()
                    deleting = null
                }) { Text(stringResource(com.onefilm.android.tv.compose.R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(com.onefilm.android.tv.compose.R.string.settings_cancel))
                }
            },
        )
    }
}

@Composable
private fun MobileCentered(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun SortRow(
    key: Int,
    ascending: Boolean,
    onKeyChange: (Int) -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            0 to com.onefilm.android.tv.compose.R.string.netdisk_sort_name,
            1 to com.onefilm.android.tv.compose.R.string.netdisk_sort_date,
            2 to com.onefilm.android.tv.compose.R.string.netdisk_sort_type,
            3 to com.onefilm.android.tv.compose.R.string.netdisk_sort_size,
        ).forEach { (value, labelRes) ->
            val active = key == value
            Text(
                text = stringResource(labelRes),
                fontSize = 13.sp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    )
                    .clickable { onKeyChange(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(
            text = if (ascending) "↑" else "↓",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { onToggle() }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DriveRow(
    drive: NetdiskDrive,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val url = remember(drive.configJson) {
        runCatching { JSONObject(drive.configJson).optString("url") }.getOrDefault("")
    }
    val typeLabel = stringResource(
        when (drive.type) {
            0 -> com.onefilm.android.tv.compose.R.string.netdisk_local_dir
            1 -> com.onefilm.android.tv.compose.R.string.netdisk_type_webdav
            else -> com.onefilm.android.tv.compose.R.string.netdisk_type_alist
        },
    )
    // 主框（类型 · 名称 · 地址）与「编辑 / 删除」分离：按钮不再放进同一个框里。
    // 行间距统一交给 LazyColumn（与设置面板一致 4dp），这里不再额外加 padding。
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    RoundedCornerShape(10.dp),
                )
                .clickable { onOpen() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = typeLabel,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = drive.name.ifBlank { stringResource(com.onefilm.android.tv.compose.R.string.netdisk_unnamed) },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
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
        PanelButton(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_edit), onClick = onEdit)
        Spacer(Modifier.width(6.dp))
        PanelButton(
            stringResource(com.onefilm.android.tv.compose.R.string.common_delete),
            danger = true,
            onClick = onDelete,
        )
    }
}

/** 与设置面板「加载」按钮同款：32dp 高、10dp 圆角、1dp 描边、surfaceVariant 底。 */
@Composable
private fun PanelButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EntryRow(entry: NetdiskEntry, onClick: () -> Unit) {
    // 与 TV 列表同一套设计：单行、框高与设置面板一致，图标 + 名称 + 时间 + 类型 + 大小。
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.isDirectory) Icons.Outlined.Folder else Icons.Outlined.Movie,
            contentDescription = null,
            tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = entry.name,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (entry.modified > 0L) formatShortTime(entry.modified) else "—",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = entryTypeLabel(entry),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(46.dp),
        )
        Text(
            text = if (entry.isDirectory) "" else NetdiskSort.formatSize(entry.size),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.width(62.dp),
        )
    }
}

@Composable
private fun entryTypeLabel(entry: NetdiskEntry): String {
    if (entry.isDirectory) {
        return stringResource(com.onefilm.android.tv.compose.R.string.netdisk_type_folder)
    }
    return NetdiskSort.extensionOf(entry.name).uppercase(Locale.getDefault()).ifBlank { "—" }
}

private fun formatShortTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))


/** 扁平紧凑的输入框：与设置面板一致（圆角 10dp、34dp 最小高度、聚焦描边）。 */
@Composable
private fun CompactField(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                RoundedCornerShape(10.dp),
            ),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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

@Composable
private fun MobileDriveDialog(
    drive: NetdiskDrive?,
    onDismiss: () -> Unit,
    onSave: (NetdiskDrive) -> Unit,
) {
    var type by remember(drive?.id) { mutableStateOf(drive?.type ?: 1) }
    var name by remember(drive?.id) { mutableStateOf(drive?.name.orEmpty()) }
    var url by remember(drive?.id) { mutableStateOf("") }
    var initPath by remember(drive?.id) { mutableStateOf("") }
    var username by remember(drive?.id) { mutableStateOf("") }
    var password by remember(drive?.id) { mutableStateOf("") }
    var token by remember(drive?.id) { mutableStateOf("") }
    var localUri by remember(drive?.id) { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(drive?.id) {
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

    val nameRequired = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_name_required)
    val urlRequired = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_url_required)
    val dirRequired = stringResource(com.onefilm.android.tv.compose.R.string.netdisk_dir_required)
    val cancelText = stringResource(com.onefilm.android.tv.compose.R.string.settings_cancel)
    val saveText = stringResource(com.onefilm.android.tv.compose.R.string.settings_save)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        0 to com.onefilm.android.tv.compose.R.string.netdisk_local_dir,
                        1 to com.onefilm.android.tv.compose.R.string.netdisk_type_webdav,
                        2 to com.onefilm.android.tv.compose.R.string.netdisk_type_alist,
                    ).forEach { (value, labelRes) ->
                        val active = type == value
                        Text(
                            text = stringResource(labelRes),
                            fontSize = 13.sp,
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                )
                                .clickable { type = value; error = null }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                CompactField(name, stringResource(com.onefilm.android.tv.compose.R.string.netdisk_space_name), { name = it })
                Spacer(Modifier.height(8.dp))
                when (type) {
                    0 -> Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = localUri.ifBlank {
                                stringResource(com.onefilm.android.tv.compose.R.string.netdisk_no_dir)
                            },
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showPicker = true }) {
                            Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_select_dir))
                        }
                    }
                    1 -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactField(
                            url,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_webdav_url),
                            { url = it },
                            KeyboardType.Uri,
                        )
                        CompactField(
                            initPath,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_init_path),
                            { initPath = it },
                        )
                        CompactField(
                            username,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_username),
                            { username = it },
                        )
                        CompactField(
                            password,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_password),
                            { password = it },
                            KeyboardType.Password,
                        )
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompactField(
                            url,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_alist_url),
                            { url = it },
                            KeyboardType.Uri,
                        )
                        CompactField(
                            initPath,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_init_path),
                            { initPath = it },
                        )
                        CompactField(
                            password,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_password),
                            { password = it },
                            KeyboardType.Password,
                        )
                        CompactField(
                            token,
                            stringResource(com.onefilm.android.tv.compose.R.string.netdisk_alist_token),
                            { token = it },
                        )
                    }
                }
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(cancelText, fontSize = 14.sp) }
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = {
                        when {
                            name.isBlank() -> error = nameRequired
                            type == 0 && localUri.isBlank() -> error = dirRequired
                            type != 0 && url.isBlank() -> error = urlRequired
                            else -> {
                                val json = JSONObject().apply {
                                    if (type == 0) {
                                        put("uri", localUri)
                                    } else {
                                        put("url", url.trim().trimEnd('/') + "/")
                                        put("initPath", initPath.trim().trim('/'))
                                        put("username", username.trim())
                                        put("password", password)
                                        put("token", token.trim())
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
                        }
                    }) { Text(saveText, fontSize = 14.sp) }
                }
            }
        }
    }

    if (showPicker) {
        MobileFolderPicker(
            initial = localUri,
            onDismiss = { showPicker = false },
            onPick = { picked ->
                localUri = if (picked.startsWith("file://")) picked else "file://$picked"
                if (name.isBlank()) name = java.io.File(picked).name.orEmpty()
                showPicker = false
            },
        )
    }
}

/** 手机端目录选择：只列目录，点选进入，底部确认当前目录。 */
@Composable
private fun MobileFolderPicker(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val start = remember(initial) {
        val raw = initial.removePrefix("file://")
        val candidate = if (raw.startsWith("/")) java.io.File(raw) else null
        if (candidate != null && candidate.isDirectory) candidate
        else android.os.Environment.getExternalStorageDirectory() ?: java.io.File("/storage")
    }
    var dir by remember { mutableStateOf(start) }
    val children = remember(dir) {
        runCatching {
            dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                .orEmpty()
        }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_select_dir), fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    dir.absolutePath,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dir.parentFile?.let { parent -> dir = parent } }) {
                        Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_parent_dir), fontSize = 13.sp)
                    }
                    TextButton(onClick = { onPick(dir.absolutePath) }) {
                        Text(stringResource(com.onefilm.android.tv.compose.R.string.netdisk_use_dir), fontSize = 13.sp)
                    }
                }
                LazyColumn(Modifier.height(300.dp)) {
                    items(children) { child ->
                        Text(
                            text = child.name,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { dir = child }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(com.onefilm.android.tv.compose.R.string.settings_cancel))
            }
        },
    )
}