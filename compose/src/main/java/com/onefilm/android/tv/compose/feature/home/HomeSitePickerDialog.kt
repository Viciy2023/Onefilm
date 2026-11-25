package com.onefilm.android.tv.compose.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.HomeSiteOption
import kotlinx.coroutines.delay

/**
 * 「主页」标签再点一次弹出的首页数据源面板。
 *
 * <p>与设置面板同一策略：TV 与手机共用这一个组件，按屏幕宽度自适应
 * （电视是网格 + 遥控器焦点，手机是单列 + 触摸滚动），不写两套 UI。
 */
@Composable
fun HomeSitePickerDialog(
    options: List<HomeSiteOption>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val touch = LocalConfiguration.current.screenWidthDp < 600
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (touch) 0.92f else 0.62f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(R.string.home_pick_site),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                if (options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_pick_site_empty),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SiteGrid(options, touch, onPick)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_cancel),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteGrid(
    options: List<HomeSiteOption>,
    touch: Boolean,
    onPick: (String) -> Unit,
) {
    val initial = remember(options) { options.firstOrNull { it.current } ?: options.first() }
    val initialFocus = remember { FocusRequester() }
    LaunchedEffect(options) {
        if (touch) return@LaunchedEffect
        delay(120)
        runCatching { initialFocus.requestFocus() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (touch) 1 else 3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (touch) 420.dp else 300.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(options, key = { it.key }) { option ->
            SiteRow(
                option = option,
                modifier = if (option.key == initial.key) Modifier.focusRequester(initialFocus) else Modifier,
                onClick = { onPick(option.key) },
            )
        }
    }
}

@Composable
private fun SiteRow(
    option: HomeSiteOption,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    // 样式与点播页顶部接口分类标签（主页 / 热门电影 / …）完全一致：
    // 8dp 圆角、32dp 高、横向渐变底、未聚焦 1dp / 聚焦 2dp 主色描边、13sp 文字。
    val shape = RoundedCornerShape(8.dp)
    val highlighted = focused || option.current
    val foreground =
        if (highlighted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .fillMaxWidth()
            .height(32.dp)
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
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.name.ifBlank { option.key },
            fontSize = 13.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (option.current) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_pick_site_current),
                fontSize = 11.sp,
                color = foreground.copy(alpha = 0.85f),
                maxLines = 1,
            )
        }
    }
}
