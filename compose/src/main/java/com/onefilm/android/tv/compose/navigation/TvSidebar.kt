package com.onefilm.android.tv.compose.navigation

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SidebarEntry(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * TV 左侧导航栏：9 项固定在一屏内，不滚动。
 * 样式与导航按钮一致：34dp 高、10dp 圆角、横向渐变 + 细边框。
 */
@Composable
fun TvSidebar(
    entries: List<SidebarEntry>,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier
            .width(120.dp)
            .fillMaxHeight()
            .padding(start = 6.dp, top = 2.dp, end = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        entries.forEachIndexed { index, entry ->
            SidebarButton(
                entry = entry,
                focusRequester = if (index == 0) firstFocusRequester else null,
            )
        }
    }
}

@Composable
private fun SidebarButton(
    entry: SidebarEntry,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val highlighted = entry.selected || focused
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(32.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
            )
            .clip(shape)
            .background(
                if (highlighted) {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.onSurface,
                            MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        ),
                    )
                },
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape,
            )
            .clickable(interactionSource = interaction, indication = null) {
                entry.onClick()
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = entry.icon,
            contentDescription = entry.label,
            tint = contentColor,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = entry.label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
