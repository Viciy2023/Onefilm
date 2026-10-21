package com.onefilm.android.tv.compose.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AdaptiveNavigation(
    isTv: Boolean,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    showTabs: Boolean = true,
    tvHeader: (@Composable () -> Unit)? = null,
    transparentBackground: Boolean = false,
    wallpaper: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val items = listOf(
        Routes.HOME to "首页",
        Routes.SEARCH to "搜索",
        Routes.LIBRARY to "收藏",
        Routes.LIVE to "直播",
        Routes.SETTINGS to "设置",
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = if (transparentBackground) Color.Transparent else MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        wallpaper?.invoke()
        if (isTv) {
            Column(Modifier.fillMaxSize()) {
                if (tvHeader != null) {
                    tvHeader()
                } else if (showTabs) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 48.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        items.forEach { (route, label) ->
                            NavTab(label = label, selected = currentRoute == route) { onNavigate(route) }
                        }
                    }
                }
                Box(Modifier.weight(1f).statusBarsPadding()) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).statusBarsPadding()) { content() }
                if (showTabs) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        items.forEach { (route, label) ->
                            NavTab(label = label, selected = currentRoute == route) { onNavigate(route) }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}
