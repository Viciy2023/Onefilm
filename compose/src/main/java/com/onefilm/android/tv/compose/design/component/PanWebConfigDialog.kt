package com.onefilm.android.tv.compose.design.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R

/**
 * 「网页版配置中心」：接口只提供网页入口，这里用统一弹窗展示地址，
 * 需要时再打开网页本身，不再直接跳到深色页面。
 */
@Composable
fun PanWebConfigDialog(
    url: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "网页版配置中心",
        subtitle = "接口自带的网页配置页。竖屏 App 上一般用不到，需要时再打开。",
        onDismiss = onDismiss,
        actions = {
            AppDialogAction(
                label = stringResource(R.string.settings_cancel),
                modifier = Modifier.weight(1f),
                autoFocus = true,
                onClick = onDismiss,
            )
            AppDialogAction(
                label = "打开网页",
                modifier = Modifier.weight(1f),
                primary = true,
                onClick = onOpen,
            )
        },
    ) {
        PanRowBox {
            Text(
                text = url,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
