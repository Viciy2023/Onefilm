package com.onefilm.android.tv.compose.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onefilm.android.tv.compose.bridge.HomeCategory
import com.onefilm.android.tv.compose.feature.category.CategoryFilterController
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val TvInk = Color(0xFF1C1C1E)

object TvBrandState {
    var notice by mutableStateOf("")
    var siteName by mutableStateOf("")
}

object TvNavState {
    var lastLabel by mutableStateOf("首页")
}

data class TvNavEntry(
    val label: String,
    val icon: String,
    val route: String? = null,
    val onClick: () -> Unit,
)

enum class TvHeaderVariant {
    Full,
    CategoryOnly,
}

@Composable
fun TvHeader(
    currentRoute: String?,
    categories: List<HomeCategory>,
    notice: String?,
    siteName: String? = null,
    variant: TvHeaderVariant = TvHeaderVariant.Full,
    selectedCategoryKey: String? = null,
    selectedCategoryId: String? = null,
    onNavigate: (String) -> Unit,
    onLive: () -> Unit,
    onConfig: () -> Unit,
    onLine: () -> Unit,
    onNetwork: () -> Unit,
    onCategory: (HomeCategory) -> Unit,
) {
    val centerText = notice?.takeIf { it.isNotBlank() }
        ?: siteName?.takeIf { it.isNotBlank() }
        ?: "ONEFILM 影视"
    val entries = listOf(
        TvNavEntry("首页", "⌂", route = Routes.HOME) { onNavigate(Routes.HOME) },
        TvNavEntry("历史", "↺", route = Routes.HISTORY) { onNavigate(Routes.HISTORY) },
        TvNavEntry("收藏", "★", route = Routes.LIBRARY) { onNavigate(Routes.LIBRARY) },
        TvNavEntry("搜索", "⌕", route = Routes.SEARCH) { onNavigate(Routes.SEARCH) },
        TvNavEntry("直播", "▶") { onLive() },
        TvNavEntry("配置", "⚙") { onConfig() },
        TvNavEntry("线路", "⇄") { onLine() },
        TvNavEntry("网盘", "☁") { onNetwork() },
        TvNavEntry("设置", "☰", route = Routes.SETTINGS) { onNavigate(Routes.SETTINGS) },
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
    ) {
        // TV 盒子在不同设备上可能上报 1.0 或 2.5 的 density。
        // 用可用宽度做一次自适应，避免 2400px 屏上导航和分类缩成一小条。
        val scale = (maxWidth.value / 1000f).coerceIn(1f, 2.4f)
        val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val navFocusRequesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
        val initialCategoryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(variant, currentRoute, selectedCategoryKey, selectedCategoryId) {
            delay(600)
            when (variant) {
                TvHeaderVariant.Full -> {
                    val target = navFocusRequesters[TvNavState.lastLabel]
                        ?: navFocusRequesters["首页"]
                    target?.requestFocus()
                }
                TvHeaderVariant.CategoryOnly -> {
                    if (selectedCategoryKey != null && selectedCategoryId != null) {
                        runCatching { CategoryFilterController.categoryFocusRequester.requestFocus() }
                    } else {
                        initialCategoryFocus.requestFocus()
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth()) {
        TvBrandRow(
            centerText = centerText,
            compact = variant == TvHeaderVariant.CategoryOnly,
        )

        if (variant == TvHeaderVariant.Full) {
        // 第二行：功能导航。九项等权铺满，图标 + 文字。
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    val y = stroke / 2f
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = stroke,
                    )
                }
                .padding(horizontal = 48.dp, vertical = (4 * scale).dp),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            entries.forEach { entry ->
                val focusRequester = navFocusRequesters.getOrPut(entry.label) {
                    androidx.compose.ui.focus.FocusRequester()
                }
                TvNavTab(
                    entry = entry,
                    modifier = Modifier.weight(1f),
                    uiScale = scale,
                    selected = false,
                    focusRequester = focusRequester,
                )
            }
        }
        }

        // 第三行：分类标签。从左往右自然排列，超出屏宽后横向滑动。
        val allCategories = listOf(HomeCategory("", "", "主页")) + categories
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    start = 48.dp,
                    top = if (variant == TvHeaderVariant.CategoryOnly) (4 * scale).dp else (2 * scale).dp,
                    end = 48.dp,
                    bottom = (2 * scale).dp,
                ),
            horizontalArrangement = Arrangement.spacedBy((6 * scale).dp),
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
                    onClick = {
                        if (home) onNavigate(Routes.HOME) else onCategory(category)
                    },
                    scale = scale,
                    selected = false,
                    focusRequester = when {
                        current -> CategoryFilterController.categoryFocusRequester
                        index == 0 && selectedCategoryKey == null -> initialCategoryFocus
                        else -> null
                    },
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)))
        }
    }
}

@Composable
fun TvBrandRow(
    centerText: String = "",
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 48.dp,
) {
    val resolvedCenter = centerText.takeIf { it.isNotBlank() }
        ?: TvBrandState.notice.takeIf { it.isNotBlank() }
        ?: TvBrandState.siteName.takeIf { it.isNotBlank() }
        ?: "ONEFILM 影视"
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1_000)
        }
    }
    val date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = (maxWidth.value / 1000f).coerceIn(1f, 2.4f)
        Row(
            Modifier.fillMaxWidth().padding(
                horizontal = horizontalPadding,
                vertical = ((if (compact) 2 else 6) * scale).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = "ONEFILM",
                    color = TvInk,
                    fontSize = ((if (compact) 18 else 28) * scale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (compact) 1.sp else 2.sp,
                )
            }
            Box(Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                Text(
                    text = resolvedCenter,
                    color = TvInk,
                    fontSize = (18 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = "$date   $time",
                    color = TvInk,
                    fontSize = ((if (compact) 18 else 16) * scale).sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TvNavTab(
    entry: TvNavEntry,
    modifier: Modifier = Modifier,
    uiScale: Float = 1f,
    selected: Boolean = false,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val localFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val targetFocus = focusRequester ?: localFocus
    val shape = RoundedCornerShape(16.dp)
    val highlighted = selected || focused
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier.height((34 * uiScale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .focusRequester(targetFocus)
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
                    TvNavState.lastLabel = entry.label
                    targetFocus.requestFocus()
                    entry.onClick()
                }
                .padding(horizontal = (8 * uiScale).dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = entry.icon,
                color = contentColor,
                fontSize = (14 * uiScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width((5 * uiScale).dp))
            Text(
                text = entry.label,
                color = contentColor,
                fontSize = (16 * uiScale).sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TvCategoryChip(
    category: HomeCategory,
    onClick: () -> Unit,
    scale: Float = 1f,
    selected: Boolean = false,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val localFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val targetFocus = focusRequester ?: localFocus
    val shape = RoundedCornerShape(12.dp)
    val highlighted = selected || focused
    Text(
        text = category.name.ifBlank { "未命名" },
        color = if (highlighted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        fontSize = (16 * scale).sp,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .focusRequester(targetFocus)
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
                targetFocus.requestFocus()
                onClick()
            }
            .padding(horizontal = (14 * scale).dp, vertical = (6 * scale).dp),
    )
}
