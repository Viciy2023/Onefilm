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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
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

object TvHeaderFocusState {
    var homeCategoryFocused by mutableStateOf(false)
    var navFocusedOnce by mutableStateOf(false)
    var homeRoute by mutableStateOf(false)
    var firstNavRequester: androidx.compose.ui.focus.FocusRequester? = null
}

object TvBrandState {
    var notice by mutableStateOf("")
    var siteName by mutableStateOf("")
}

object TvNavState {
    var lastLabel by mutableStateOf("历史")
}

data class TvNavEntry(
    val label: String,
    val icon: ImageVector,
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
        TvNavEntry("历史", Icons.Outlined.History, route = Routes.HISTORY) { onNavigate(Routes.HISTORY) },
        TvNavEntry("收藏", Icons.Outlined.Star, route = Routes.LIBRARY) { onNavigate(Routes.LIBRARY) },
        TvNavEntry("搜索", Icons.Outlined.Search, route = Routes.SEARCH) { onNavigate(Routes.SEARCH) },
        TvNavEntry("直播", Icons.Outlined.LiveTv) { onLive() },
        TvNavEntry("配置", Icons.Outlined.Tune) { onConfig() },
        TvNavEntry("线路", Icons.Outlined.SwapHoriz) { onLine() },
        TvNavEntry("网盘", Icons.Outlined.Cloud) { onNetwork() },
        TvNavEntry("设置", Icons.Outlined.Settings, route = Routes.SETTINGS) { onNavigate(Routes.SETTINGS) },
    )

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.8f)),
    ) {
        // TV 与手机使用同一套 sp/dp 基准，不再按屏宽二次放大字体，避免 4K 电视溢出。
        val scale = 1f
        val focusManager = LocalFocusManager.current
        val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        val navFocusRequesters = remember { mutableMapOf<String, androidx.compose.ui.focus.FocusRequester>() }
        val initialCategoryFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        val firstNavFocus = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(firstNavFocus) { TvHeaderFocusState.firstNavRequester = firstNavFocus }
        var navFocusEnabled by remember { mutableStateOf(variant != TvHeaderVariant.Full) }
        var categoryFocusSeen by remember { mutableStateOf(false) }
        var homeCategoryFocused by remember { mutableStateOf(false) }
        LaunchedEffect(variant, currentRoute, selectedCategoryKey, selectedCategoryId, categories) {
            if (variant == TvHeaderVariant.Full) {
                navFocusEnabled = false
                categoryFocusSeen = false
            }
            delay(100)
            when (variant) {
                TvHeaderVariant.Full -> {
                    if (selectedCategoryKey != null && selectedCategoryId != null) {
                        runCatching { CategoryFilterController.categoryFocusRequester.requestFocus() }
                    } else {
                        // Home navigation was removed: the API category "主页" is the
                        // single home entry. Focus it directly on launch/back.
                        runCatching { initialCategoryFocus.requestFocus() }
                        delay(80)
                        if (!navFocusEnabled) {
                            runCatching { focusManager.moveFocus(FocusDirection.Down) }
                        }
                    }
                }
                TvHeaderVariant.CategoryOnly -> {
                    if (selectedCategoryKey != null && selectedCategoryId != null) {
                        runCatching { CategoryFilterController.categoryFocusRequester.requestFocus() }
                    } else {
                        runCatching { initialCategoryFocus.requestFocus() }
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (homeCategoryFocused &&
                        event.type == KeyEventType.KeyDown &&
                        event.key == Key.DirectionDown
                    ) {
                        homeCategoryFocused = false
                        navFocusEnabled = true
                        runCatching { firstNavFocus.requestFocus() }
                        true
                    } else false
                },
        ) {
        TvBrandRow(
            centerText = centerText,
            compact = variant == TvHeaderVariant.CategoryOnly,
        )

        // 第二行：接口分类。"主页"是唯一主页入口。
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
                    selected = home && currentRoute == Routes.HOME && !categoryFocusSeen,
                    autoFocus = home && currentRoute == Routes.HOME,
                    onFocused = {
                        categoryFocusSeen = true
                        homeCategoryFocused = home && currentRoute == Routes.HOME
                        TvHeaderFocusState.homeCategoryFocused = home && currentRoute == Routes.HOME
                        TvHeaderFocusState.navFocusedOnce = false
                        if (home) navFocusEnabled = true
                    },
                    downFocus = if (home && currentRoute == Routes.HOME) firstNavFocus else null,
                    onFocusTrigger = {
                        when {
                            home && currentRoute != Routes.HOME -> onNavigate(Routes.HOME)
                            !home && !current -> onCategory(category)
                        }
                    },
                    focusRequester = when {
                        index == 0 -> initialCategoryFocus
                        current -> CategoryFilterController.categoryFocusRequester
                        else -> null
                    },
                )
            }
        }
        if (variant == TvHeaderVariant.Full) {
        // 第三行：功能导航。九项等权铺满，图标 + 文字。
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
                val focusRequester = if (entry.label == "历史") firstNavFocus else navFocusRequesters.getOrPut(entry.label) {
                    androidx.compose.ui.focus.FocusRequester()
                }
                TvNavTab(
                    entry = entry,
                    modifier = Modifier.weight(1f),
                    uiScale = scale,
                    selected = false,
                    focusEnabled = navFocusEnabled,
                    onFocused = { TvHeaderFocusState.navFocusedOnce = true },
                    focusRequester = focusRequester,
                )
            }
        }
        }

        // 第二行：分类标签。从左往右自然排列，超出屏宽后横向滑动。
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
                    fontSize = ((if (compact) 16 else 22) * scale).sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (compact) 1.sp else 2.sp,
                )
            }
            Box(Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                Text(
                    text = resolvedCenter,
                    color = TvInk,
                    fontSize = (14 * scale).sp,
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
                    fontSize = ((if (compact) 14 else 14) * scale).sp,
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
    focusEnabled: Boolean = true,
    onFocused: () -> Unit = {},
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val localFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val targetFocus = focusRequester ?: localFocus
    val shape = RoundedCornerShape(10.dp)
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
                .onFocusChanged { if (it.isFocused) onFocused() }
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
            Icon(
                imageVector = entry.icon,
                contentDescription = entry.label,
                tint = contentColor,
                modifier = Modifier.size((16 * uiScale).dp),
            )
            Spacer(Modifier.width((5 * uiScale).dp))
            Text(
                text = entry.label,
                color = contentColor,
                fontSize = (14 * uiScale).sp,
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
    autoFocus: Boolean = false,
    onFocused: () -> Unit = {},
    onFocusTrigger: () -> Unit = {},
    downFocus: androidx.compose.ui.focus.FocusRequester? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val localFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val targetFocus = focusRequester ?: localFocus
    var autoFocusRequested by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) onFocused()
    }
    LaunchedEffect(focused) {
        if (focused) {
            // Category pages load on focus; a short debounce avoids firing
            // while the remote is passing through items.
            delay(100)
            if (focused) onFocusTrigger()
        }
    }
    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            autoFocusRequested = false
            // Retry quickly until the category chip actually owns focus.
            // This runs only while autoFocus is true and stops once focused.
            repeat(12) {
                if (focused) return@LaunchedEffect
                runCatching { targetFocus.requestFocus() }
                delay(40)
            }
        }
    }
    val shape = RoundedCornerShape(8.dp)
    val highlighted = selected || focused
    Text(
        text = category.name.ifBlank { "未命名" },
        color = if (highlighted) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
        fontSize = (14 * scale).sp,
        fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        modifier = modifier
            .focusRequester(targetFocus)
            .onFocusChanged {
                if (it.isFocused) onFocused()
            }
            .focusProperties {
                if (downFocus != null) down = downFocus
            }
            .onGloballyPositioned {
                if (autoFocus && !autoFocusRequested) {
                    autoFocusRequested = true
                    runCatching { targetFocus.requestFocus() }
                }
            }
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
