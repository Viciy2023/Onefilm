package com.onefilm.android.tv.compose.design.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onefilm.android.tv.compose.R

/** 统一加载状态：应用 LOGO 匀速旋转。 */
@Composable
fun AppLogoLoading(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val transition = rememberInfiniteTransition(label = "app-logo-loading")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "app-logo-angle",
    )
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = "加载中",
        modifier = modifier.size(size).rotate(angle),
    )
}
