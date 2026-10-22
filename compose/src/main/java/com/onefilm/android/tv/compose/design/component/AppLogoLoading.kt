package com.onefilm.android.tv.compose.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R

/** 统一加载状态：应用 LOGO 匀速旋转（不依赖系统动画时长设置）。 */
@Composable
fun AppLogoLoading(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    var angle by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val seconds = (now - last) / 1_000_000_000f
                last = now
                angle = (angle + seconds * (360f / 1.1f)) % 360f
            }
        }
    }
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = "加载中",
        modifier = modifier
            .size(size * 0.5f)
            .clip(CircleShape)
            .graphicsLayer { rotationZ = angle },
    )
}

@Composable
fun AppLoadingState(
    text: String = "哎呀！正在加载中呢，你等等呀！",
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        AppLogoLoading(size = 84.dp)
        Spacer(Modifier.height(14.dp))
        Text(text, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
