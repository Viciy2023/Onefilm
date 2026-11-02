package com.onefilm.android.tv.mobileui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.CategoryDataSource
import com.onefilm.android.tv.compose.bridge.CategoryFilterGroup
import com.onefilm.android.tv.compose.bridge.CategoryPage
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.bridge.MediaCard
import com.onefilm.android.tv.mobileui.component.MobileHorizontalGrid

private sealed interface MobileCategoryState {
    data object Loading : MobileCategoryState
    data object Error : MobileCategoryState
    data class Content(val page: CategoryPage) : MobileCategoryState
}

/** 分类页最少行数；屏幕更高（平板）时按可用高度自动增加行数。 */
private const val MIN_ROWS = 4

/** 目标行高，用来换算行数：手机约 4 行，平板会自动变多。 */
private val TARGET_ROW_HEIGHT = 190.dp

@Composable
fun MobileCategoryScreen(
    category: HomeCategory,
    dataSource: CategoryDataSource,
    onBack: () -> Unit,
    onCardClick: (MediaCard) -> Unit,
) {
    var state by remember(category.key, category.id) {
        mutableStateOf<MobileCategoryState>(MobileCategoryState.Loading)
    }
    var retry by remember { mutableIntStateOf(0) }
    var selectedFilters by remember(category.key, category.id) {
        mutableStateOf<Map<String, String>>(emptyMap())
    }
    var filterGroups by remember(category.key, category.id) {
        mutableStateOf<List<CategoryFilterGroup>>(emptyList())
    }
    var showFilters by remember(category.key, category.id) { mutableStateOf(false) }
    LaunchedEffect(category.key, category.id, retry, selectedFilters) {
        state = MobileCategoryState.Loading
        state = runCatching {
            dataSource.load(
                key = category.key,
                tid = category.id,
                selected = selectedFilters,
            )
        }.fold(
            onSuccess = {
                filterGroups = it.filters
                MobileCategoryState.Content(it)
            },
            onFailure = { MobileCategoryState.Error },
        )
    }
    val availableFilters = filterGroups

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回") }
            Text(
                text = category.name.ifBlank { "分类" },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            if (availableFilters.isNotEmpty()) {
                TextButton(onClick = { showFilters = true }) { Text("筛选") }
            }
        }
        when (val current = state) {
            MobileCategoryState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            MobileCategoryState.Error -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("分类加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { retry++ }, modifier = Modifier.padding(top = 12.dp)) {
                    Text("重试")
                }
            }
            is MobileCategoryState.Content -> BoxWithConstraints(Modifier.fillMaxSize()) {
                // 统一的左右滑动网格：4 行起步（平板按高度自动加行），左右连续滑动，不分页。
                val rowGap = 8.dp
                val colGap = 8.dp
                val rows = ((maxHeight - 16.dp) / TARGET_ROW_HEIGHT).toInt().coerceAtLeast(MIN_ROWS)
                val rowHeight = ((maxHeight - rowGap * (rows - 1) - 16.dp) / rows)
                    .coerceAtLeast(96.dp)
                val cardWidth = (rowHeight * 2f / 3f).coerceAtLeast(72.dp)
                MobileHorizontalGrid(
                    items = current.page.items,
                    rows = rows,
                    rowHeight = rowHeight,
                    cardWidth = cardWidth,
                    rowGap = rowGap,
                    colGap = colGap,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    onCardClick = onCardClick,
                )
            }
        }
    }

    if (showFilters && availableFilters.isNotEmpty()) {
        MobileCategoryFilterDialog(
            groups = availableFilters,
            selected = selectedFilters,
            onSelect = { selectedFilters = it },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun MobileCategoryFilterDialog(
    groups: List<CategoryFilterGroup>,
    selected: Map<String, String>,
    onSelect: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 430.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                groups.forEach { group ->
                    item(key = "group:${group.key}") {
                        Text(
                            text = group.name.ifBlank { group.key },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    item(key = "options:${group.key}") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(group.options, key = { it.value }) { option ->
                                val checked = selected[group.key] == option.value
                                Text(
                                    text = option.name.ifBlank { option.value },
                                    fontSize = 12.sp,
                                    color = if (checked) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (checked) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant,
                                        )
                                        .clickable {
                                            onSelect(
                                                if (checked) selected - group.key
                                                else selected + (group.key to option.value),
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}
