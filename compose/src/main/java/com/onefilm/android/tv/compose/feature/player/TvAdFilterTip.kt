package com.onefilm.android.tv.compose.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.core.adblock.AdFilterNotifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TV 广告过滤成功提示。
 *
 * 放在宿主根布局的最上层，详情页小窗与全屏播放都能看到；
 * 扁平圆角、无阴影，5 秒后自动淡出。颜色来自 [MaterialTheme]，
 * 天然跟随「简约 / 暗黑 / 跟随系统」。
 */
@Composable
fun TvAdFilterTip(modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var token by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        val listener = AdFilterNotifier.Listener { _ ->
            scope.launch {
                token++
                visible = true
            }
        }
        AdFilterNotifier.addListener(listener)
        onDispose { AdFilterNotifier.removeListener(listener) }
    }

    LaunchedEffect(token, visible) {
        if (visible) {
            delay(5_000)
            visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
        exit = fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(10.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
        ) {
            Text(
                text = stringResource(R.string.ad_filter_success_tip),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}
