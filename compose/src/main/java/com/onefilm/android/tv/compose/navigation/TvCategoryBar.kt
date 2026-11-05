package com.onefilm.android.tv.compose.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.R
import com.onefilm.android.tv.compose.bridge.HomeCategory
import kotlinx.coroutines.delay

/**
 * 点播页顶部的接口分类行（主页 / 热门电影 / …）。
 * 只在点播内容区显示，可横向滚动，焦点移动时自动滚入视野。
 */
@Composable
fun TvCategoryBar(
    currentRoute: String?,
    categories: List<HomeCategory>,
    selectedCategoryKey: String?,
    selectedCategoryId: String?,
    onNavigate: (String) -> Unit,
    onCategory: (HomeCategory) -> Unit,
    hasCategoryFilters: (HomeCategory) -> Boolean,
    onHomeFocused: (Boolean) -> Unit,
) {
    val allCategories = listOf(HomeCategory("", "", stringResource(R.string.category_home))) + categories
    val initialFocus = remember { FocusRequester() }
    var categoryFocusSeen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        runCatching { initialFocus.requestFocus() }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 12.dp, top = 2.dp, end = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        allCategories.forEachIndexed { index, category ->
            val home = category.key.isBlank()
            val current = if (home) {
                currentRoute == Routes.HOME
            } else {
                category.key == selectedCategoryKey && category.id == selectedCategoryId
            }
            TvCategoryChip(
                category = category,
                hasFilter = !home && current && hasCategoryFilters(category),
                onClick = {
                    if (home) onNavigate(Routes.HOME) else onCategory(category)
                },
                selected = home && currentRoute == Routes.HOME && !categoryFocusSeen,
                onFocused = {
                    categoryFocusSeen = true
                    onHomeFocused(home)
                },
                leftEdge = index == 0,
                rightEdge = index == allCategories.lastIndex,
                onFocusTrigger = {
                    when {
                        home && currentRoute != Routes.HOME -> onNavigate(Routes.HOME)
                        !home && !current -> onCategory(category)
                    }
                },
                focusRequester = if (index == 0) initialFocus else null,
            )
        }
    }
}

@Composable
private fun TvCategoryChip(
    category: HomeCategory,
    hasFilter: Boolean = false,
    onClick: () -> Unit,
    selected: Boolean = false,
    onFocused: () -> Unit = {},
    onFocusTrigger: () -> Unit = {},
    leftEdge: Boolean = false,
    rightEdge: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val localFocus = remember { FocusRequester() }
    val targetFocus = focusRequester ?: localFocus
    LaunchedEffect(focused) {
        if (focused) {
            onFocused()
        }
    }
    LaunchedEffect(focused) {
        if (focused) {
            delay(100)
            if (focused) onFocusTrigger()
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val highlighted = selected || focused
    val foreground =
        if (highlighted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .focusRequester(targetFocus)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusProperties {
                // 行首/行尾自锁，避免默认几何搜索在尽头甩到下方内容。
                if (leftEdge) left = targetFocus
                if (rightEdge) right = targetFocus
            }
            .clip(shape)
            .height(32.dp)
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
                targetFocus.requestFocus()
                onClick()
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.name.ifBlank { stringResource(R.string.category_unnamed) },
            color = foreground,
            fontSize = 13.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        if (hasFilter) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = stringResource(R.string.category_filter),
                tint = foreground.copy(alpha = if (highlighted) 0.85f else 0.65f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}
