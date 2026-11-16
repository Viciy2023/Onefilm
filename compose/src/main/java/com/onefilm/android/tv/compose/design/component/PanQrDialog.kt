package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.PanConfirmState
import com.onefilm.android.tv.compose.bridge.PanProviders
import com.onefilm.android.tv.compose.bridge.PanQrState
import com.onefilm.android.tv.compose.bridge.QrCodes
import com.onefilm.android.tv.compose.bridge.statusValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 二维码弹窗：统一走 [AppDialog]，样式随主题。
 * 只做「标题 + 说明 + 二维码 + 关闭」，不再自绘边框/标题栏。
 */
@Composable
fun PanQrDialog(
    title: String,
    message: String,
    content: String,
    onClose: () -> Unit,
) {
    val heightDp = LocalConfiguration.current.screenHeightDp
    // 二维码按屏高自适应，保证 1080p 下二维码和按钮同时可见
    val size = min(320f, heightDp * 0.40f).dp
    val image = remember(content) {
        when {
            content.isBlank() -> null
            QrCodes.isImage(content) -> QrCodes.bitmapFromDataUri(content)?.asImageBitmap()
            else -> QrCodes.bitmap(content)?.asImageBitmap()
        }
    }
    AppDialog(
        title = title,
        subtitle = message,
        onDismiss = onClose,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.pan_close),
                modifier = Modifier.weight(1f),
                primary = true,
                autoFocus = true,
                onClick = onClose,
            )
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                )
            }
        }
    }
}

/** 扫码登录弹窗：请求二维码 → 轮询 → 成功/失败自动结束。 */
@Composable
fun PanLoginDialog(
    siteKey: String,
    categoryId: String,
    title: String,
    onClose: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val provider = remember(categoryId, title) { PanProviders.login(categoryId, title) }
    var qr by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("正在生成二维码…") }
    LaunchedEffect(siteKey, provider) {
        if (provider == null) {
            message = "该平台不支持扫码登录"
            return@LaunchedEffect
        }
        val result = actions?.startLogin(siteKey, provider, title)
        if (result == null) {
            message = "二维码生成失败，请检查接口是否可用"
            return@LaunchedEffect
        }
        qr = result.text
        message = result.message
        while (true) {
            delay(1500)
            when (val state = actions.pollLogin(siteKey, provider)) {
                is PanQrState.Success -> {
                    onClose()
                    break
                }
                is PanQrState.Error -> {
                    message = state.message
                    qr = null
                    break
                }
                else -> Unit
            }
        }
    }
    PanQrDialog(title = title, message = message, content = qr.orEmpty(), onClose = onClose)
}

/** 注册二维码弹窗：链接取自接口 `action=status`。 */
@Composable
fun PanRegisterDialog(
    siteKey: String,
    categoryId: String,
    title: String,
    onClose: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val field = remember(categoryId, title) { PanProviders.registerField(categoryId, title) }
    var link by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("正在获取注册链接…") }
    LaunchedEffect(siteKey, field) {
        val value = field?.let { actions?.statusValue(siteKey, it) }
        if (value.isNullOrBlank()) {
            message = "接口暂未返回注册链接"
        } else {
            link = value
            message = "用手机扫码打开注册链接。"
        }
    }
    PanQrDialog(title = title, message = message, content = link.orEmpty(), onClose = onClose)
}

/** Cookie / Token 手动输入：统一弹窗 + 多行输入框 + 保存。 */
@Composable
fun PanCookieDialog(
    siteKey: String,
    categoryId: String,
    title: String,
    onClose: () -> Unit,
    onSaved: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val actions = ComposeDependencies.panConfigActions
    val provider = remember(categoryId, title) { PanProviders.manual(categoryId, title) }
    val setter = remember(provider) { PanProviders.cookieSetter(provider) }
    AppDialog(
        title = title,
        subtitle = "粘贴后点保存，会写入接口配置并刷新状态。",
        onDismiss = onClose,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                enabled = !saving,
                autoFocus = true,
                onClick = onClose,
            )
            AppDialogAction(
                label = stringResource(R.string.pan_save),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !saving && setter != null && text.isNotBlank(),
                onClick = {
                    if (saving) return@AppDialogAction
                    saving = true
                    scope.launch {
                        if (setter != null && text.isNotBlank()) {
                            actions?.runAction(
                                siteKey,
                                "action=$setter&value=" + android.net.Uri.encode(text.trim()),
                            )
                        }
                        saving = false
                        onSaved()
                        onClose()
                    }
                },
            )
        },
    ) {
        if (setter == null) {
            Text("该平台暂不支持手动输入", fontSize = 13.sp)
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().height(150.dp),
                placeholder = { Text(PanProviders.cookieHint(provider), fontSize = 13.sp) },
            )
        }
    }
}

/** 二次确认弹窗（清空 Cookie / Token 等）。 */
@Composable
fun PanConfirmDialog(
    state: PanConfirmState,
    onDone: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    AppDialog(
        title = state.title,
        subtitle = state.message,
        onDismiss = onDone,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                enabled = !busy,
                autoFocus = true,
                onClick = onDone,
            )
            AppDialogAction(
                label = stringResource(R.string.pan_confirm),
                modifier = Modifier.weight(1f),
                primary = true,
                enabled = !busy,
                onClick = {
                    if (busy) return@AppDialogAction
                    busy = true
                    scope.launch {
                        actions?.runAction(state.siteKey, "action=" + state.action)
                        busy = false
                        onDone()
                    }
                },
            )
        },
    ) {
        if (busy) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        } else if (!state.body.isNullOrBlank()) {
            Text(state.body, fontSize = 13.sp)
        }
    }
}

/** 信息提示弹窗（例如二维码获取失败、占位说明）。 */
@Composable
fun PanInfoDialog(
    title: String,
    message: String,
    onClose: () -> Unit,
) {
    AppDialog(
        title = title,
        subtitle = message,
        onDismiss = onClose,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.pan_close),
                modifier = Modifier.weight(1f),
                primary = true,
                autoFocus = true,
                onClick = onClose,
            )
        },
    ) {
    }
}
