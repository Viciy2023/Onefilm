package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.PanFormSpec
import com.onefilm.android.tv.compose.bridge.PanForms
import kotlinx.coroutines.launch

/**
 * 通用表单弹窗：账号密码登录、手机号验证码登录、DIY 配置地址、WebDAV 备份。
 *
 * 行样式与「配置 → 线路」一致（见 [PanRow]），底部固定 取消 / 清除 / 保存。
 */
@Composable
fun PanFormDialog(
    siteKey: String,
    spec: PanFormSpec,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val scope = rememberCoroutineScope()
    val values = remember(spec) {
        mutableStateMapOf<String, String>().apply {
            spec.fields.forEach { field -> put(field.key, field.value) }
        }
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }

    fun run(query: String, closeOnSuccess: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            val result = actions?.runCommand(siteKey, PanForms.buildQuery(query, values))
            busy = false
            if (result != null) {
                message = result.message
                messageOk = result.ok
                if (result.ok && closeOnSuccess) onDone()
            } else {
                message = "接口不可用"
                messageOk = false
            }
        }
    }

    AppDialog(
        title = spec.title,
        subtitle = spec.subtitle,
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                enabled = !busy,
                autoFocus = true,
                onClick = onDone,
            )
            spec.clearQuery?.let { query ->
                AppDialogAction(
                    label = spec.clearLabel ?: "清除",
                    modifier = Modifier.weight(1f),
                    enabled = !busy,
                    onClick = { run(query, true) },
                )
            }
            if (spec.saveQuery != null) {
                AppDialogAction(
                    label = spec.saveLabel,
                    modifier = Modifier.weight(1f),
                    primary = true,
                    enabled = !busy,
                    onClick = { run(spec.saveQuery, true) },
                )
            }
        },
    ) {
        spec.fields.forEach { field ->
            PanTextField(
                value = values[field.key].orEmpty(),
                onValueChange = { values[field.key] = it },
                placeholder = field.label,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (field.multiline) 96.dp else 34.dp),
                singleLine = !field.multiline,
                visualTransformation = if (field.key == "pass" || field.key == "password") {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
            )
        }
        if (spec.extras.isNotEmpty()) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                spec.extras.forEach { extra ->
                    PanRow(
                        label = extra.label,
                        enabled = !busy,
                    ) { run(extra.query, false) }
                }
            }
        }
        message?.let { text ->
            Text(
                text = text,
                fontSize = 12.sp,
                color = if (messageOk) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}
