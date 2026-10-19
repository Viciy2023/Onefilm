package com.onefilm.android.tv.compose.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** M3 播放器占位页：后续接入 PlayerManager 与 Compose 控制层。 */
@Composable
fun PlayerRoute(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Text(
            text = "‹",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clickable(onClick = onBack),
        )
        Text(
            text = "播放器（M3 施工中）",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
