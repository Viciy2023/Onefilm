package com.onefilm.android.tv.compose.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.HomeCategory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val TvInk = Color(0xFF1C1C1E)
private val TvMuted = Color(0xFF6C6C70)
private val TvSurface = Color(0xFFF2F2F7)

data class TvNavEntry(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

@Composable
fun TvHeader(
    currentRoute: String?,
    categories: List<HomeCategory>,
    notice: String?,
    onNavigate: (String) -> Unit,
    onLive: () -> Unit,
    onConfig: () -> Unit,
    onLine: () -> Unit,
    onNetwork: () -> Unit,
    onCategory: (HomeCategory) -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "ONEFILM",
                color = TvInk,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = notice?.takeIf { it.isNotBlank() } ?: "",
                color = TvMuted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(520.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "$date   $time",
                color = TvInk,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val entries = listOf(
            TvNavEntry("首页", currentRoute?.startsWith("home") == true) { onNavigate(Routes.HOME) },
            TvNavEntry("搜索", currentRoute?.startsWith("search") == true) { onNavigate(Routes.SEARCH) },
            TvNavEntry("收藏", currentRoute?.startsWith("library") == true) { onNavigate(Routes.LIBRARY) },
            TvNavEntry("历史", false) { onNavigate(Routes.LIBRARY) },
            TvNavEntry("直播", false, onLive),
            TvNavEntry("配置", false, onConfig),
            TvNavEntry("线路", false, onLine),
            TvNavEntry("网盘", false, onNetwork),
            TvNavEntry("设置", currentRoute?.startsWith("settings") == true) { onNavigate(Routes.SETTINGS) },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                TvNavTab(entry)
            }
        }
        if (categories.isNotEmpty()) {
            LazyRow(
                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(categories) { category ->
                    TvCategoryChip(category) { onCategory(category) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
    }
}

@Composable
private fun TvNavTab(entry: TvNavEntry) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, tween(120), label = "tv-nav")
    val shape = RoundedCornerShape(18.dp)
    Text(
        text = entry.label,
        color = if (entry.selected) MaterialTheme.colorScheme.background else TvInk,
        fontSize = 15.sp,
        fontWeight = if (entry.selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 10.dp else 0.dp, shape)
            .clip(shape)
            .background(if (entry.selected) TvInk else Color.Transparent)
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = entry.onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    )
}

@Composable
private fun TvCategoryChip(category: HomeCategory, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = category.name.ifBlank { "未命名" },
        color = TvInk,
        fontSize = 13.sp,
        modifier = Modifier
            .shadow(if (focused) 8.dp else 0.dp, shape)
            .clip(shape)
            .background(if (focused) Color.White else TvSurface)
            .border(if (focused) 2.dp else 0.dp, Color.White.copy(alpha = 0.4f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}
