package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.PanEmbyServer
import com.onefilm.android.tv.compose.bridge.statusValue
import kotlinx.coroutines.launch

/**
 * Emby 服务器管理：列表可选中（选择当前服务器）、可删除、可测试，
 * 下面对应「新增 / 修改」表单，保存走 `action=setEmbyServer`。
 */
@Composable
fun PanEmbyDialog(
    siteKey: String,
    title: String,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<PanEmbyServer>>(emptyList()) }
    var current by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }

    LaunchedEffect(siteKey, reload) {
        loading = true
        servers = actions?.embyServers(siteKey).orEmpty()
        current = actions?.statusValue(siteKey, "embyChoose")?.toIntOrNull() ?: 0
        loading = false
    }

    fun run(query: String, closeOnSuccess: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = actions?.runCommand(siteKey, query)
            busy = false
            if (result != null) {
                message = result.message
                messageOk = result.ok
                if (result.ok) {
                    if (closeOnSuccess) onDone() else reload++
                }
            } else {
                message = "接口不可用"
                messageOk = false
            }
        }
    }

    AppDialog(
        title = title,
        subtitle = "点某一台服务器即可切换为当前使用；下面可以新增或覆盖配置。",
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.pan_close),
                modifier = Modifier.weight(1f),
                enabled = !busy,
                autoFocus = true,
                onClick = onDone,
            )
            AppDialogAction(
                label = "删除",
                modifier = Modifier.weight(1f),
                enabled = !busy && servers.isNotEmpty(),
                onClick = { run("action=deleteEmbyServer&index=$current", false) },
            )
            AppDialogAction(
                label = "测试",
                modifier = Modifier.weight(1f),
                enabled = !busy && servers.isNotEmpty(),
                onClick = { run("action=testEmbyServer&index=$current", false) },
            )
            AppDialogAction(
                label = stringResource(R.string.pan_save),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !busy && name.isNotBlank() && url.isNotBlank(),
                onClick = {
                    run(
                        "action=setEmbyServer&index=-1&name=" + android.net.Uri.encode(name.trim()) +
                            "&url=" + android.net.Uri.encode(url.trim()) +
                            "&username=" + android.net.Uri.encode(user.trim()) +
                            "&password=" + android.net.Uri.encode(pass),
                        false,
                    )
                },
            )
        },
    ) {
        if (loading) {
            Text("正在读取服务器列表…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (servers.isEmpty()) {
            Text("暂无服务器，请在下面新增。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            servers.forEachIndexed { index, server ->
                PanRow(
                    label = (index + 1).toString() + ". " + server.name.ifBlank { "Emby " + (index + 1) },
                    secondary = server.url,
                    selected = index == current,
                    enabled = !busy,
                ) {
                    current = index
                    run("action=chooseEmbyServer&index=$index", false)
                }
            }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EmbyField("服务器名称", name) { name = it }
            EmbyField("服务器地址（https://emby.example.com）", url) { url = it }
            EmbyField("用户名", user) { user = it }
            EmbyField("密码", pass, password = true) { pass = it }
        }
        message?.let { text ->
            Text(
                text = text,
                fontSize = 12.sp,
                color = if (messageOk) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EmbyField(
    label: String,
    value: String,
    password: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        singleLine = true,
        placeholder = { Text(label, fontSize = 13.sp) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
    )
}
