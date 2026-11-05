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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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
import com.onefilm.android.tv.compose.design.component.TvTagButton
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
            entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.netdisk_empty_dir), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    .onFailure { error = it.message ?: context.getString(R.string.netdisk_parse_failed) }
                                loading = false
                            }
                        }
                    }
                }
                if (drive.type == 2 && entries.size >= 100) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        drives.forEach { drive ->
            // 行本身不再 clickable：主体、编辑、删除必须是同级焦点，否则遥控器进不到按钮上
            var bodyFocused by remember(drive.id) { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        if (bodyFocused) 2.dp else 1.dp,
                        if (bodyFocused) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        RoundedCornerShape(18.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (bodyFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            else Color.Transparent,
                        )
                        .onFocusChanged { bodyFocused = it.isFocused }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpen(drive) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) { Text(if (drive.type == 1) "DAV" else "AL", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(drive.name.ifBlank { stringResource(R.string.netdisk_unnamed) }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (drive.type == 1) stringResource(R.string.netdisk_type_webdav) else stringResource(R.string.netdisk_type_alist),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                }
                DriveActionButton(stringResource(R.string.netdisk_edit)) { onEdit(drive) }
                DriveActionButton(stringResource(R.string.common_delete)) { onDelete(drive) }
            }
        }
    }
}

@Composable
private fun DriveActionButton(text: String, onClick: () -> Unit) {
    TvTagButton(
        text = text,
        modifier = Modifier.padding(start = 4.dp),
        onClick = onClick,
    )
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
                    listOf(0 to stringResource(R.string.netdisk_local_dir), 1 to stringResource(R.string.netdisk_type_webdav), 2 to stringResource(R.string.netdisk_type_alist)).forEach { (value, label) ->
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
                FieldRow(stringResource(R.string.netdisk_space_name)) {
                    CompactField(name, stringResource(R.string.netdisk_name_hint), { name = it })
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
                            TvTagButton(text = stringResource(R.string.netdisk_select_dir), onClick = { folderPicker.launch(null) })
                        }
                    }
                    1 -> {
                        FieldRow(stringResource(R.string.netdisk_webdav_url)) { CompactField(url, "https://example.com/dav/", { url = it }) }
                        FieldRow(stringResource(R.string.netdisk_init_path)) { CompactField(initPath, stringResource(R.string.netdisk_init_path_hint), { initPath = it }) }
                        FieldRow(stringResource(R.string.netdisk_username)) { CompactField(username, stringResource(R.string.netdisk_optional), { username = it }) }
                        FieldRow(stringResource(R.string.netdisk_password)) { CompactField(password, stringResource(R.string.netdisk_optional), { password = it }) }
                    }
                    else -> {
                        FieldRow(stringResource(R.string.netdisk_alist_url)) { CompactField(url, "https://alist.example.com/", { url = it }) }
                        FieldRow(stringResource(R.string.netdisk_init_path)) { CompactField(initPath, stringResource(R.string.netdisk_init_path_hint), { initPath = it }) }
                        FieldRow(stringResource(R.string.netdisk_password)) { CompactField(password, stringResource(R.string.netdisk_optional), { password = it }) }
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