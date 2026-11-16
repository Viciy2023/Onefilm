package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.LocalConfiguration
import com.onefilm.android.tv.compose.R
import androidx.compose.ui.res.stringResource
import com.onefilm.android.tv.compose.bridge.ComposeDependencies
import com.onefilm.android.tv.compose.bridge.PanQrState
import com.onefilm.android.tv.compose.bridge.QrCodes
import kotlinx.coroutines.delay

/**
 * 网盘二维码弹窗：标题 + 说明 + 二维码 + 关闭。
 *
 * 不做额外装饰，和接口页面自己的弹窗一致（白底、居中二维码）。
 */
@Composable
fun PanQrDialog(
    title: String,
    message: String,
    content: String,
    onClose: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val image = remember(content) { QrCodes.bitmap(content, if (wide) 360 else 480)?.asImageBitmap() }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (wide) 0.6f else 0.92f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            modifier = Modifier
                                .size(if (wide) 300.dp else 240.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(androidx.compose.ui.graphics.Color.White),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onClose)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Text(stringResource(R.string.pan_close), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/** 扫码登录弹窗：打开即请求二维码，1.5 秒轮询一次，成功/失败自动关闭。 */
@Composable
fun PanLoginDialog(
    siteKey: String,
    provider: String,
    title: String,
    onClose: () -> Unit,
) {
    val actions = ComposeDependencies.panConfigActions
    var qr by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("正在生成二维码…") }
    LaunchedEffect(siteKey, provider) {
        val result = actions?.startLogin(siteKey, provider, title)
        if (result == null) {
            message = "二维码生成失败"
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
                    break
                }
                else -> Unit
            }
        }
    }
    val content = qr
    if (content != null) {
        PanQrDialog(title = title, message = message, content = content, onClose = onClose)
    } else {
        PanQrDialog(title = title, message = message, content = "", onClose = onClose)
    }
}
